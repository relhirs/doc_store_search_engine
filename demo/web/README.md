# Demo web app

The page for the live demo. It runs the engine from `demo/engine` in a Web Worker, so each visitor gets a private store and there's no server.

    ../engine/verify.sh
    npm install
    npm run dev

`verify.sh` builds `engine.js`, which this app imports straight from `../engine/target/javascript/`. The build stops with a message if that file is missing, so CI has to build the engine first.

## Checking it

    npm run build
    npm run preview
    npm run e2e

`npm run e2e` needs the preview running in another terminal. It drives the production build in Chromium: the full tour, the memory wall, awkward input, reduced motion, a phone layout and a failed engine. Set `CHROME_PATH` if Chrome for Testing isn't in Playwright's cache, and `SITE` to check a different address, such as the live site.

`npm run record` also needs the preview running, plus ffmpeg. It replays the tour and rewrites `public/walkthrough.gif` and `walkthrough.png`, which the root README and the failed-engine page show, so run it after any visible change.

## How it fits together

- `src/demo/useDemo.ts` holds the page's state and every call to the engine.
- `src/engine/client.ts` keeps calls in order. Its `latest` method drops stale searches and limits, so fast input skips to the newest value.
- `src/components/Wall.tsx` places every tile from engine state. RAM runs from most recent to the next to leave, using `nextToEvict` and not list order.
- `src/demo/tour.ts` lists the tour's stops. `src/components/TourOverlay.tsx` draws the dim, the box and the arrow, and `src/demo/placement.ts` works out where the card goes. A stop's `target` matches a `data-tour` attribute on the feature it highlights.
- `src/corpus.ts` holds the 24 articles. Search is case sensitive and only indexes ASCII letters and digits, so keep the text to those.

Searching counts as a use, so anything that only needs to look at the index calls `indexView`, which changes nothing.
