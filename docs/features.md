# Features

## Overview

URL Shortener is a self-hosted tool that turns long, unwieldy URLs into short, shareable links. It is built for teams and individuals who want click tracking, expiry controls, and geographic insights without relying on a third-party service.

---

## How short links work

When you create a link, the app assigns it a short code (e.g. `aB3kR2p`). Your shareable link looks like:

```
https://yourdomain.com/aB3kR2p
```

Anyone who visits that address is immediately redirected to the original destination. The redirect is instant — visitors do not see any intermediate page unless the link has expired or been deactivated, in which case they land on a "Link expired" page instead.

---

## Creating a short link

Open the **Create Short Link** form on the main page and fill in the fields below.

### Destination URL (required)
The full address you want to shorten — for example, `https://example.com/very/long/path?with=parameters`. This is the only field you must fill in.

### Custom code (optional)
By default the app generates a short code for you (e.g. `aB3kR2p`). If you would prefer a memorable slug — such as `summer-sale` — type it here. It must be unique; you will see an error if someone else has already claimed it.

### Short code strategy (optional)
Controls how the auto-generated code is created when you have not supplied a custom code. Three options are available:

| Strategy | What it does |
|---|---|
| **RANDOM_BASE62** *(default)* | Picks a random mix of letters and digits. A different code each time, even for the same URL. |
| **HASH_TRUNCATE** | Derives the code from the URL itself. The same URL will always produce the same code. |
| **SEQUENTIAL** | Uses a simple counter: the first link is `a`, the second `b`, and so on. Predictable and easy to audit. |

The strategy is locked in at creation and cannot be changed later.

#### Strategy options
Each strategy exposes a small set of tuning options (shown when you select a strategy):

- **Length** (RANDOM_BASE62 and HASH_TRUNCATE) — how many characters long the code should be. Between 4 and 20; defaults to 7.
- **Algorithm** (HASH_TRUNCATE) — which hashing method to use: `SHA-256` (default) or `SHA-512`.
- **Prefix** (SEQUENTIAL) — a short label prepended to the counter, e.g. `s-` to produce `s-a`, `s-b`, etc. Up to 16 characters, letters/digits/hyphens/underscores only.

### Tags (optional)
Attach one or more comma-separated labels to a link, for example `marketing,promo,q3`. Tags appear as clickable badges in your links list, letting you filter down to just the links that share a tag.

### Max clicks (optional)
Set a click cap. Once that number of clicks is reached the link stops working. Useful for limited-time offers or exclusive invites.

### Expires at (optional)
Pick a date and time when the link should automatically stop working. After that moment, anyone who visits the short URL is shown an "link expired" page.

---

## Link management

All your short links appear in the **Your Links** table. From there you can:

- **Filter by tag** — click any tag badge to show only links that carry that tag. Click the "Clear filter" button to go back to the full list.
- **Copy the short URL** — click the short code in the table; it opens in a new tab so you can copy it from the address bar.
- **View a QR code** — click **QR** on any row to see a scannable QR code for that link.
- **Edit** — click **Edit** to change the destination URL, tags, click cap, expiry date, or active status. The short code and strategy cannot be changed after creation.
- **Deactivate / reactivate** — in the edit form, toggle the Active switch off to make a link stop working immediately (without deleting it), or back on to restore it.
- **Delete** — click **Delete** to permanently remove a link and all its recorded click history.

---

## Analytics

Click **Stats** on any row to open the analytics panel for that link. It shows:

- **Total clicks** — the running lifetime count of times the short link has been followed.
- **Clicks over time** — a bar chart with one bar per day, so you can see spikes and trends at a glance.

The panel slides open below the table and stays open until you close it with the **×** button in the corner.

---

## Geo analytics

When geographic data collection is enabled, the analytics panel also shows:

- **Top countries** — a horizontal bar chart ranking the countries your visitors come from, by click count.
- **Top cities** — a similar chart broken down to city level, labelled as "City, Country".

If geo analytics have not been configured for your installation, or if a link has not yet received any clicks with location data, the panel shows a note that geographic data is not yet available. Enabling this feature requires loading an optional location database on the server; it is not on by default.

---

## Link expiry and limits

A link can stop working for any of three reasons:

1. **It was manually deactivated.** An admin or the link owner turned it off via the Edit form.
2. **Its expiry date has passed.** Once the date and time you set in "Expires at" is reached, the link stops redirecting — even if it has plenty of clicks remaining.
3. **It hit its click cap.** Once the "Max clicks" limit is reached, the very next visitor will see the expired page. The counter is checked on every visit, so the limit is enforced accurately.

In all three cases the visitor is shown a **Link expired** page rather than a generic browser error. This page is part of the app, so it can be styled to match your brand. The link itself and its click history are not deleted — they remain in your list so you can reactivate or inspect them.
