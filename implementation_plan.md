# Redesign plan

**Goal:** make ReelScore look and feel like a professionally designed app, using the direction you approved.

- **Colours stay the same:** light grey background, white cards, near-black text, lime accent, dark nav bar. No new colours, and no dark theme by default (dark mode keeps following the phone).
- **What changes:** layout, type, components, motion and depth of features.
- **What doesn't change:** counting, accounts, journeys, limits and settings work exactly as now.

## 1. Structure: 5 tabs become 4

| Tab | What's on it |
|---|---|
| **Home** | The dashboard (section 3) |
| **Insights** | Today's Trends plus Apps, with the new deeper insights |
| **Friends** | Podium, leaderboard, requests |
| **You** | Profile, counter, hours, limit, journey, settings gear |

- Every Home tile opens a **full detail page**: Distance, Busiest hours, Journey, App detail, Friends.
- Pages open with a smooth expand from the tile they came from, so the app feels connected rather than tab-to-tab.

## 2. Design system (`ui/theme`, `ui/components`)

**Kept as is:**
- **Colours:** the existing lime, olive, graphite and greys only. Tints of lime are used for heatmaps, and nothing else is new.
- **Fonts:** Bricolage Grotesque for numbers and titles, Roboto for body text. The type sizes get tightened.

**New:**
- **Spacing:** one 4 dp grid.
- **Corners:** cards 22 dp, controls 16 dp, chips as pills.

**New shared components:**
- `HeroCard`: today's count, status tag, the comparison with yesterday, and a line chart of how the day built up.
- `StatTile`: half-width card with a label, a big number, a mini visual and a tap-through.
- `PillTabs`: the black-pill switch for Today / Week / All time. It replaces the plain Material segmented buttons that looked basic.
- `StatusTag`: small tags such as "Counting", "Paused" and "Set hours".
- `Avatar`: an initial in a circle, with lime for you.
- `Sparkline`, `HourHeatmap` (7 days × 24 hours) and `CalendarHeatmap` (month grid), drawn on a Canvas in lime tints.
- `Podium`: the top 3 at different heights.
- `SectionHeader`: a title with a "See all" chevron.
- `BottomSheet` for Add friend, Requests and a friend's card.

**Motion:**
- Numbers count up.
- Cards scale to 98% when pressed.
- Cards fade and rise one after another on first load.
- Charts draw in.
- Everything respects the phone's "remove animations" setting.

## 3. Screens

### Home (replaces Today)
- **Header:** the date, a greeting by time of day, and your avatar, which opens You.
- **When something needs you:** a slim card for "Counter is off" or the battery note, above the hero card.
- **Hero card:** reels today, a status tag, "5 more than yesterday", and a line chart from 6 am to now.
- **Two tiles:**
  - **Scrolled:** distance, a progress bar and "98% of a bus". Opens Distance.
  - **Busiest hour:** today's peak hour and a mini heatmap. Opens Insights.
- **Friends strip:** overlapping avatars and "You're 2nd of 3 today". Opens Friends. Signed out, it shows a small invite instead.
- **Journey card**, if a journey is active: rings and progress. Opens Journey.
- **By app:** a compact row of app icons with counts. Each opens App detail.
- **Daily limit**, if on: a thin progress bar toward the limit, in neutral wording.

### Insights (Trends and Apps merged)
- `PillTabs`: Week / Month.
- The existing bar chart, restyled, with journey pairing kept.
- Stats grid: daily average, busiest day, this week, all time.
- **New:** an hour heatmap of which hours you watch most over the last 4 weeks, and the top 3 hours.
- **New:** a calendar heatmap of the month. Tapping a day shows that day's count and apps.
- **Apps:** each app with a 7-day sparkline and its share. Opens App detail.
- **Distance** card, which opens Distance.

### Distance page (new)
- Today / All time.
- The picture comparison, bigger.
- "Up next": the next 3 objects with how far away each is ("cricket pitch in 8.3 m").

### Friends
- `PillTabs`: Today / Week / All time.
- A small "Friends | Everyone" switch.
- `Podium` for the top 3, then a clean list. Private friends show "Private".
- Two buttons: **Add friend** (sheet) and **Requests · n** (sheet).
- Tapping a friend opens their card with today, week and all time, and Remove.
- Sort "Most first" / "Fewest first" stays, as a small icon button.
- Signed out: a designed welcome card explaining what's shared, with Sign in with Google.

### You
- **Profile header:** avatar, username and a privacy chip, which opens privacy options.
- **Counter card,** restyled with a status tag.
- **Counting hours, daily limit and journey**, as the same rows restyled.
- **Gear** for Settings. Settings, Help and Journey pages get the new headers and rows.

## 4. Data needed (new queries; nothing new leaves the phone)
- `ReelDao.hourlyCounts(start, end)`: counts per hour, for today's line chart and the hour heatmap.
- `ReelDao.weekdayHourCounts(since)`: a 7×24 grid for the last 4 weeks.
- `dailyCounts(days)` already exists; it's reused for the calendar and per-app sparklines, with an app filter added.

## 5. Files
- **[NEW]**
  - `ui/home/HomeScreen.kt`
  - `ui/insights/InsightsScreen.kt`
  - `ui/distance/DistanceScreen.kt`
  - `ui/components/{HeroCard, StatTile, PillTabs, StatusTag, Avatar, Charts (Sparkline, HourHeatmap, CalendarHeatmap), Podium, SectionHeader, Sheets}.kt`
- **[MODIFY]**
  - `ui/AppNav.kt`: 4 tabs and the new routes.
  - `FloatingNavBar.kt`: style tweaks only, no behaviour change.
  - `ui/friends/FriendsScreen.kt`, `ui/you/YouScreen.kt`, `ui/settings/*`, `ui/journey/JourneyScreen.kt`, `ui/app/AppDetailScreen.kt`: restyle.
  - `ui/theme/{Type, Shape}.kt`
  - `data/ReelDao.kt`, `data/ReelRepository.kt`: new queries.
- **[DELETE]** `ui/today/TodayScreen.kt`, `ui/trends/TrendsScreen.kt` and `ui/apps/AppsScreen.kt`. Their parts move into Home and Insights.
- The lag fix stays: tabs still live in `TabHost` and are never rebuilt on switch.

## 6. Order of work
1. Design system pieces and the new queries.
2. Home.
3. Insights and Distance.
4. Friends.
5. You and the restyled inner pages.
6. Build the release version, install it, and measure smoothness with `design/measure_tabs.ps1`.
7. Screenshots of every screen in light and dark mode, sent to you.

## 7. Not in this batch
- The remaining 24 Canva pictures, after the limit resets on 1 Nov.
- Share card, home screen widget and weekly recap (not chosen).
