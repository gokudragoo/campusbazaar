# CampusBazaar: agent guide

CampusBazaar is the buy-and-sell marketplace inside your college. Students sell used books,
calculators, cycles, coolers, hostel gear and notes to other students on the same campus, chat
and negotiate with offer cards, and meet at a safe spot on campus. Currency is ₹ and everything
is scoped to the student's campus.

Two deliverables live in this repo:

- `server/`: Next.js 16 (App Router route handlers only, TypeScript strict) deployed to Vercel
  project `campusbazaar-api`. Neon Postgres via Drizzle, Vercel Blob for photos, Gemini vision
  for listing drafts, Nexconn Platform Chat API for tokens, community channels, system notices
  and message metadata.
- `android/`: native Kotlin + Jetpack Compose app on the Nexconn Android Chat SDK
  (`ai.nexconn.chat:chat`), Singapore data center.

## User journeys

### First-time student
1. Opens the app, sees a short welcome, taps "Create account": name, college email (any email),
   password (8+ chars). Validation errors appear inline under each field.
2. Picks a campus: searches by name ("IIT Delhi", "Christ University"). If it is missing, adds it
   with name + city. The server dedupes ("I.I.T. Delhi" equals "IIT delhi"), creates the campus's
   Nexconn community channel with #general, #books, #electronics, #rides-and-trips and
   #lost-and-found, and joins the student automatically.
3. Lands on Browse: an empty campus shows an empty state that invites them to sell the first item.
4. Optional profile: photo, hostel, department.

### Seller: sell in under a minute
1. Taps Sell, takes photos with the in-app camera or picks up to 5 from the photo picker.
2. Photos upload through the server to Blob. Gemini vision drafts title, category, condition,
   description and a fair used-price range in ₹. The seller edits anything.
3. Sets price, negotiable yes/no, preferred meetup spot (suggested campus spots or free text) and
   publishes. If AI is busy, the form stays fully usable by hand.
4. Manages listings from Profile: edit, mark reserved, mark sold (picks the buyer from people who
   chatted), or delete (with confirmation).

### Buyer: browse and find
1. Campus feed (newest first), category chips, search, filters (price range, condition, sort).
2. Listing detail: photo pager, price tag, condition, meetup spot, seller card (rating, response
   rate, items sold), save, share-free safety tips.
3. Saved items list. Wanted posts ("Need an Engineering Graphics drafter"): anyone can post;
   sellers respond with one of their listings or a note, which opens a chat.

### Chat and negotiate
1. "Chat with seller" opens the Nexconn direct channel between buyer and seller. A listing card
   (custom message) anchors the negotiation for that listing.
2. Offer cards (custom message, ₹ amount, Accept / Counter / Decline). State is stored on the
   server and mirrored into the offer message's Nexconn metadata, so both phones update live.
3. Accepting an offer marks the listing Reserved for that buyer.
4. Meetup card proposes a campus spot + time (optionally a Nexconn location message with the
   current GPS position). The other side confirms or declines.
5. After meeting, the seller marks it Sold; both sides rate each other (1-5 stars + comment).
6. Text, images, typing indicator, read receipts, unread badges, conversation list.

### Campus Square
Each campus has a Nexconn community channel with five subchannels for open discussion. Students
join automatically when they pick the campus.

### Safety
Block a user (Nexconn blocklist + server record hides their listings both ways), report a user or
listing, hide a listing from your feed, and safe-meetup tips in chat and on listing detail.

### Returning student and edge cases
- Session (JWT) and Nexconn token are kept in encrypted storage; the app reconnects silently and
  refreshes the Nexconn token from the server if connect reports an invalid token.
- Offline: banner, cached feed pages, chat history from the SDK's local store, retry on failure.
- Listing sold/removed while open: detail shows the state; chat cards show the final state.
- Blocked users disappear from feed, wanted posts and cannot message you.
- Push notifications are not available (FCM would need the shared Nexconn console). The app shows
  in-app real-time notifications (banner + local notification while running) and an Activity
  inbox fed by Nexconn system-channel notices.

## Architecture (text diagram)

```
Android app (Compose, Hilt, Retrofit, Nexconn Chat SDK)
   |  HTTPS JSON (Bearer JWT)                 |  Nexconn SDK socket (SG data center)
   v                                          v
Next.js route handlers on Vercel  --------->  Nexconn Platform Chat API
   |  Drizzle (neon-http)   |  @vercel/blob     (tokens, community channels, system notices,
   v                        v                    direct messages, message metadata)
Neon Postgres           Vercel Blob (photos)
   |
   +--> Gemini (vision drafts, server-side key only)
```

## Folder map

```
server/
  src/app/api/...        route handlers (one folder per resource)
  src/lib/db/schema.ts   Drizzle schema (source of truth); migrations in server/drizzle/
  src/lib/nexconn.ts     signed Platform Chat API client
  src/lib/gemini.ts      vision listing drafts with retries + fallback model
  src/lib/auth.ts        bcrypt + JWT sessions, requireUser()
  src/lib/validation.ts  zod schemas for every input
  scripts/               migrate.ts, e2e.mjs (production smoke test)
  tests/                 vitest unit tests
android/app/src/main/java/app/campusbazaar/
  CampusBazaarApp.kt, MainActivity.kt, AppContainer.kt   app entry, manual DI, in-app notifications
  chat/NexconnManager.kt     every Nexconn SDK call (init SG, connect + token refresh, send, history, metadata, receipts, typing, blocklist)
  chat/messages/             custom message types (cbz:listing/offer/meetup/deal/notice)
  chat/ChatItems.kt          SDK Message -> UI model mapping
  data/api/                  Retrofit API + wire models, error mapping, offline cache fallback
  data/session/              Keystore-encrypted DataStore session, SessionManager (auth state, chat connect)
  data/repo/                 Auth, Market, Deal repositories, UserDirectory cache
  domain/                    pure rules (DealRules, Money, TimeText, InputRules) - unit tested
  ui/                        Compose screens per feature (auth, campus, browse, listing, sell, chats, square, wanted, profile)
  util/                      image compression, network monitor, permissions, location, notifications, LocationThumb
server/scripts/second-student.mjs   drive a second student via API + Platform Chat API (demos)
server/scripts/cleanup-e2e.mjs      remove @campusbazaar.test accounts and their campuses
docs/screenshots/                   real emulator screenshots against production
```

## Data model (Postgres)

campuses, users, listings, saved_listings, hidden_listings, wanted_posts, wanted_responses,
threads (one per listing + buyer), offers, meetups, ratings, blocks, reports, rate_limits.
See `server/src/lib/db/schema.ts`.

## Env vars (names only)

Server: `DATABASE_URL`, `DATABASE_URL_UNPOOLED`, `BLOB_READ_WRITE_TOKEN`, `NEXCONN_APP_KEY`,
`NEXCONN_APP_SECRET`, `NEXCONN_API_BASE`, `NEXCONN_ID_PREFIX`, `JWT_SECRET`, `GEMINI_API_KEY`,
`GEMINI_MODEL`, `GEMINI_FALLBACK_MODEL`, `APK_DOWNLOAD_URL`.
Android: `API_BASE_URL` build config field (no secrets in the APK; the Nexconn App Key comes
from `/api/config`).

## Commands

```
cd server
npm install
npm run dev            # http://localhost:3107
npm run typecheck
npm test               # vitest unit tests
npm run db:generate    # after schema changes
npm run db:migrate     # applies server/drizzle to Neon (uses DATABASE_URL_UNPOOLED)
vercel deploy --prod   # remote build
npm run e2e -- https://<prod-url>   # real end-to-end smoke test (Neon, Nexconn, Blob, Gemini)

cd android
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease   # needs android/keystore.properties (see README)
./gradlew --stop
```

## Conventions

- Nexconn ids: `NEXCONN_ID_PREFIX` (`cbz`) + kind + random alphanumerics. Never sequential.
- Every route goes through `route()` (consistent JSON errors) and `requireUser()`; campus-scoped
  routes use `requireCampusUser()`. Inputs are validated with zod.
- Money is whole rupees (integer).
- Commits end with the Co-Authored-By line; work lands through PRs (squash merge).

## Nexconn features and where they live

See the "How this uses Nexconn" table in README.md (kept in sync). Summary: SDK init with
AreaCode.SG, single connect with token refresh, direct channels, five custom message types,
message metadata for offer/meetup state, image messages, location messages (with server relay
fallback), read receipts, typing indicators, unread counts, conversation list, history
pagination, community channel + subchannels (created/joined via Platform Chat API, used via SDK),
system-channel notices from a bot, user blocklist, server-side direct messages and metadata.

## Testing

- Server unit tests: `cd server && npm test` (14, vitest).
- Server e2e against production: `node scripts/e2e.mjs https://campusbazaar-api.vercel.app <photo>`
  (48 checks incl. a real Gemini vision draft; cleans up its test data).
- Android unit tests: `cd android && ./gradlew testDebugUnitTest` (17 JVM tests).
- Emulator run (release APK, production): seller on device, buyer via `second-student.mjs`, then a
  second login as the buyer on the device. Screenshots in docs/screenshots.

## Known limitations

- No FCM push (shared console). In-app banners + local notifications while the process is alive,
  and the Activity inbox (system channel).
- Client-side location sends fail on this App Key with undocumented error 31610; the app falls back
  to `POST /api/chats/location`, which sends the same RC:LBSMsg via the Platform Chat API.
- SDK gotcha: `CommunitySubChannel(subChannelId, channelId)` takes the subchannel id first, unlike
  `CommunitySubChannelIdentifier(channelId, subChannelId)`.
- Typing indicators were not observed end to end (single emulator + server-driven second user).
