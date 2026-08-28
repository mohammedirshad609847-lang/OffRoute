# Offline Map Tile Bundling & Custom Data Guide

This guide explains how to bundle offline map tiles (`.mbtiles` or zip archive) into the **Offline Bus Tracker** Android application, and how to swap in your own custom bus route schedule.

---

## 1. How Offline Map Tiles Work in OSMDroid

OSMDroid supports reading offline OpenStreetMap tile archives in two standard formats:
1. `.mbtiles` files (SQLite database of raster/vector tiles generated using MOBAC or QGIS).
2. `.zip` or `.sqlite` files containing standard Z/X/Y tile directory structures.

### Method A: Bundling `.mbtiles` in App Assets
1. Obtain an `.mbtiles` file for your target city or route area (e.g. `mangalore_city.mbtiles`).
2. Place the file inside the app's assets folder:
   `app/src/main/assets/mangalore_city.mbtiles`
3. Load it into OSMDroid's tile provider in Kotlin (`OsmMapViewManager.kt`):
```kotlin
val mbTilesFile = File(context.cacheDir, "mangalore_city.mbtiles")
if (!mbTilesFile.exists()) {
    context.assets.open("mangalore_city.mbtiles").use { input ->
        FileOutputStream(mbTilesFile).use { output ->
            input.copyTo(output)
        }
    }
}
val tileSource = MapTileFileArchiveProvider(
    SimpleRegisterReceiver(context),
    FileArchiveProvider(arrayOf(mbTilesFile))
)
mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
```

### Method B: Storing Tiles in External Storage (Device SD / Download folder)
Place your `.mbtiles` file on the Android device under:
`/sdcard/osmdroid/mangalore_city.mbtiles`
OSMDroid will automatically scan `/sdcard/osmdroid/` upon startup and render the offline tiles seamlessly without requiring any internet connection or API keys.

### Method C: Zero-Dependency Vector Canvas Mode (Default Built-In Fallback)
If no map tile file is bundled, tap **"Switch to Canvas View"** in the top bar. The app renders the route, stop markers, progress, and moving bus marker on a 2D vector schematic canvas. This guarantees 100% offline functionality out-of-the-box on any Android device!

---

## 2. How to Swap in Your Own Bus Routes

The app reads route schedule data from `app/src/main/assets/demo_route.json`.

To add or modify bus stops:
1. Open `app/src/main/assets/demo_route.json`.
2. Edit the stop objects with your own names, coordinates, and scheduled times:

```json
{
  "id": "route_202",
  "name": "My Custom City Express",
  "stops": [
    {
      "id": "stop_1",
      "name": "Central Bus Stand",
      "latitude": 12.9141,
      "longitude": 74.8560,
      "scheduledTime": "08:00"
    },
    {
      "id": "stop_2",
      "name": "University Gate",
      "latitude": 12.9200,
      "longitude": 74.8650,
      "scheduledTime": "08:15"
    },
    {
      "id": "stop_3",
      "name": "Tech Park Depot",
      "latitude": 12.9260,
      "longitude": 74.8720,
      "scheduledTime": "08:30"
    }
  ]
}
```

3. Rebuild or run the app. The interpolation engine will automatically calculate smooth movement between your new stops according to your custom times!
