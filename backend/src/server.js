import "dotenv/config";
import cron from "node-cron";
import express from "express";
import mongoose from "mongoose";
import OpenAI from "openai";
import { z } from "zod";
import { zodTextFormat } from "openai/helpers/zod";

const required = ["MONGODB_URI", "PILOT_API_TOKEN"];
for (const name of required) if (!process.env[name]) throw new Error(`${name} is required`);

await mongoose.connect(process.env.MONGODB_URI);

const captureSchema = new mongoose.Schema({
  externalId: { type: String, unique: true, required: true },
  transcript: { type: String, required: true },
  capturedAt: { type: Date, required: true },
  outcome: { type: String, required: true },
  priority: { type: String, enum: ["P1", "P2"], required: true },
  dueAt: { type: Date },
  location: {
    type: { type: String, enum: ["Point"] },
    coordinates: [Number],
    accuracyMeters: Number,
  },
  whatsappDelivery: { type: String, enum: ["not_due", "queued", "sent", "failed", "not_configured"], default: "not_due" },
  whatsappError: String,
}, { timestamps: true });
captureSchema.index({ location: "2dsphere" });
captureSchema.index({ capturedAt: -1 });
captureSchema.index({ transcript: "text", outcome: "text" });
const Capture = mongoose.model("Capture", captureSchema);

const Action = z.object({
  priority: z.enum(["P1", "P2"]),
  title: z.string(),
  requiresConfirmation: z.boolean(),
  rationale: z.string(),
});

const openai = process.env.OPENAI_API_KEY ? new OpenAI({ apiKey: process.env.OPENAI_API_KEY }) : null;
const app = express();
app.use(express.json({ limit: "256kb" }));

function authorize(req, res, next) {
  if (req.get("x-pilot-token") !== process.env.PILOT_API_TOKEN) return res.status(401).json({ error: "Unauthorized" });
  next();
}

function normalizeLocation(location) {
  if (!location || typeof location.latitude !== "number" || typeof location.longitude !== "number") return undefined;
  return {
    type: "Point",
    coordinates: [location.longitude, location.latitude],
    accuracyMeters: location.accuracyMeters,
  };
}

app.get("/health", (_, res) => res.json({ ok: mongoose.connection.readyState === 1 }));

app.post("/api/interpret", authorize, async (req, res, next) => {
  try {
    if (!openai) return res.status(503).json({ error: "AI processing is not configured." });
    const transcript = String(req.body.transcript || "").trim();
    if (!transcript) return res.status(400).json({ error: "transcript is required" });
    const response = await openai.responses.parse({
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      input: [
        { role: "system", content: "Classify a personal voice note. P1 means a ringing alarm: use P1 for emergencies, urgent/critical wording, or explicit alarms. P2 means a non-urgent WhatsApp reminder. Never invent a time or send a message without confirmation." },
        { role: "user", content: transcript },
      ],
      text: { format: zodTextFormat(Action, "voice_catcher_action") },
    });
    res.json(response.output_parsed);
  } catch (error) { next(error); }
});

app.post("/api/captures", authorize, async (req, res, next) => {
  try {
    const { externalId, transcript, capturedAt, outcome, priority, dueAt, location } = req.body;
    if (!externalId || !transcript || !capturedAt || !outcome || !["P1", "P2"].includes(priority)) {
      return res.status(400).json({ error: "externalId, transcript, capturedAt, outcome, and priority are required" });
    }
    const capture = await Capture.findOneAndUpdate(
      { externalId },
      {
        $set: { transcript, capturedAt, outcome, priority, dueAt, location: normalizeLocation(location) },
        $setOnInsert: { externalId, whatsappDelivery: priority === "P2" && dueAt ? "queued" : "not_due" },
      },
      { new: true, upsert: true, runValidators: true },
    );
    res.status(201).json({ id: capture.id });
  } catch (error) { next(error); }
});

app.get("/api/captures", authorize, async (req, res, next) => {
  try {
    const query = {};
    if (req.query.q) query.$text = { $search: String(req.query.q) };
    if (req.query.from || req.query.to) query.capturedAt = { ...(req.query.from && { $gte: new Date(req.query.from) }), ...(req.query.to && { $lte: new Date(req.query.to) }) };
    if (req.query.latitude && req.query.longitude) {
      query.location = { $near: { $geometry: { type: "Point", coordinates: [Number(req.query.longitude), Number(req.query.latitude)] }, $maxDistance: Number(req.query.radiusMeters || 1000) } };
    }
    const captures = await Capture.find(query).sort({ capturedAt: -1 }).limit(100).lean();
    res.json(captures);
  } catch (error) { next(error); }
});

async function sendWhatsApp(capture) {
  const names = ["WHATSAPP_ACCESS_TOKEN", "WHATSAPP_PHONE_NUMBER_ID", "WHATSAPP_RECIPIENT_E164", "WHATSAPP_TEMPLATE_NAME"];
  if (names.some((name) => !process.env[name])) {
    capture.whatsappDelivery = "not_configured";
    await capture.save();
    return;
  }
  const body = `Reminder: ${capture.transcript}`.slice(0, 1024);
  const response = await fetch(`https://graph.facebook.com/v22.0/${process.env.WHATSAPP_PHONE_NUMBER_ID}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${process.env.WHATSAPP_ACCESS_TOKEN}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      to: process.env.WHATSAPP_RECIPIENT_E164,
      type: "template",
      template: {
        name: process.env.WHATSAPP_TEMPLATE_NAME,
        language: { code: process.env.WHATSAPP_TEMPLATE_LANGUAGE || "en_US" },
        components: [{ type: "body", parameters: [{ type: "text", text: body }] }],
      },
    }),
  });
  if (!response.ok) throw new Error(`WhatsApp Cloud API ${response.status}: ${await response.text()}`);
  capture.whatsappDelivery = "sent";
  capture.whatsappError = undefined;
  await capture.save();
}

cron.schedule("* * * * *", async () => {
  const due = await Capture.find({ priority: "P2", dueAt: { $lte: new Date() }, whatsappDelivery: "queued" }).limit(25);
  for (const capture of due) {
    try { await sendWhatsApp(capture); }
    catch (error) { capture.whatsappDelivery = "failed"; capture.whatsappError = error.message; await capture.save(); }
  }
});

app.use((error, _, res, __) => {
  console.error(error);
  res.status(500).json({ error: "Unexpected server error" });
});

app.listen(Number(process.env.PORT || 3000), () => console.log("Voice Catcher backend is running"));
