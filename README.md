# scrapers
Scrapers for various websites, written over the years, collected and maintained in one project.

Supported websites include:

* openstreetmap
* khinsider
* mangahere
* mangastream
* readcomiconlineto
* readcomicsio

## Sample Usage

```
$ bin/osm

Usage: osm [OPTIONS]

Downloads all OpenStreetMap tiles at specified zoom level, unless bounding box
has been specified. As per usage policy, limit bulk downloading up to zoom
level 16. Do NOT specify zoomlevel >= 17 unnecessarily.
For personal use only. Use responsibly!

* -z, --zoom=<zoomLevel>      required zoom level (0-19)
  -o, --output=<outDir>       output directory (default: tiles)
      --min=<minLatLon>       top left of bounding box
      --max=<maxLatLon>       bottom right of bounding box
      --resume=<resumeXY>     resume download (inclusive)
  -d, --delay=<delay>         delay between each download in milliseconds
                                (default: 1500)
  -r, --retries=<retries>     number of retries per download (default: 3)
  -h, --help                  show this help message and exit
  -V, --version               display version info

Examples
# Download all tiles from zoom 0-5
$ for i in {0..5}; do osm -z ${i}; done

# Resume from {tileserver.url}/4/5/2.png inclusive
$ osm -z 4 --resume 5,2

# Limit to bounding box (top left latlon, bottom right latlon)
$ osm -z 13 --min 1.5,103.6 --max 1.2,104.1
```

```
$ bin/khinsider

Usage: khinsider [OPTIONS] <url>

Downloads soundtracks from KHInsider.
For personal use only. Use responsibly!

*     <url>                   url of soundtrack
  -o, --output=<outDir>       output directory (default: out)
  -d, --delay=<delay>         delay between each download in milliseconds
                                (default: 1500)
  -r, --retries=<retries>     number of retries per download (default: 3)
  -h, --help                  show this help message and exit
  -V, --version               display version info

Examples
# Download all tracks
$ khinsider https://downloads.khinsider.com/game-soundtracks/album/{name}

```
