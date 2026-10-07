# Voice Catcher backend

This service stores voice-capture history in MongoDB, offers structured AI intent classification, and delivers scheduled P2 messages through the official WhatsApp Cloud API.

## Setup

1. Copy `.env.example` to `.env` and fill in every required value.
2. In `backend/`, run `npm install` and then `npm start`.
3. Deploy the service over HTTPS. For the Android private pilot, set these Gradle properties locally before building:

```properties
VOICE_CATCHER_BACKEND_URL=https://your-service.example
VOICE_CATCHER_PILOT_TOKEN=the-same-long-secret-as-the-server
```

Do not commit those properties or any `.env` file.

## WhatsApp requirement

Use a dedicated WhatsApp Business Platform number, an approved utility-message template with one body variable, and explicit opt-in from the destination number. The service sends the template when a due P2 capture is found.

## MongoDB search

`GET /api/captures` supports text search (`q`), date range (`from`, `to`), and a nearby-location query (`latitude`, `longitude`, `radiusMeters`). Every request needs the `x-pilot-token` header.
