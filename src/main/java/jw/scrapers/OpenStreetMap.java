package jw.scrapers;

import io.mikael.urlbuilder.UrlBuilder;
import jw.util.DownloadUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;

public class OpenStreetMap implements AutoCloseable {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new OSMCallable());
        cmd.registerConverter(Point2D.Double.class, value -> {
            String[] split = value.split(",");
            if (split.length != 2)
                throw new IllegalArgumentException("Invalid lat/lon format");
            double lat = Double.parseDouble(split[0]);
            double lon = Double.parseDouble(split[1]);
            return new Point2D.Double(lat, lon);
        });
        cmd.registerConverter(Point.class, value -> {
            String[] split = value.split(",");
            if (split.length != 2)
                throw new IllegalArgumentException("Invalid x/y format");
            int x = Integer.parseInt(split[0]);
            int y = Integer.parseInt(split[1]);
            return new Point(x, y);
        });
        try {
            cmd.parseWithHandlers(
                    new CommandLine.RunLast(),
                    System.out, CommandLine.Help.Ansi.AUTO,
                    new CommandLine.DefaultExceptionHandler(), args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            LOGGER.error(cause);
        }
    }

    @CommandLine.Command(
            name = "osm",
            version = "0.1.0",
            description = "%nDownloads all OpenStreetMap tiles at specified zoom level, unless bounding box has been specified. " +
                    "As per usage policy, limit bulk downloading up to zoom level 16. " +
                    "Do NOT specify zoomlevel >= 17 unnecessarily.%n" +
                    "For personal use only. Use responsibly!%n",
            footerHeading = "%nExamples%n",
            footer = "# Download all tiles from zoom 0-5%n" +
                    "$ for i in {0..5}; do osm -z ${i}; done%n%n" +
                    "# Resume from {tileserver.url}/4/5/2.png inclusive%n" +
                    "$ osm -z 4 --resume 5,2%n%n" +
                    "# Limit to bounding box (top left latlon, bottom right latlon)%n" +
                    "$ osm -z 13 --min 1.5,103.6 --max 1.2,104.1",
            sortOptions = false,
            requiredOptionMarker = '*',
            abbreviateSynopsis = true)
    private static class OSMCallable implements Callable<Void> {

        @CommandLine.Option(names = {"-z", "--zoom"}, required = true, description = "required zoom level (0-19)")
        private int zoomLevel;

        @CommandLine.Option(names = {"-o", "--output"}, description = "output directory (default: tiles)")
        private File outDir = new File("tiles");

        @CommandLine.Option(names = {"--min"}, description = "top left of bounding box")
        private Point2D.Double minLatLon;

        @CommandLine.Option(names = {"--max"}, description = "bottom right of bounding box")
        private Point2D.Double maxLatLon;

        @CommandLine.Option(names = {"--resume"}, description = "resume download (inclusive)")
        private Point resumeXY;

        @CommandLine.Option(names = {"-d", "--delay"}, description = "delay between each download in milliseconds (default: 1500)")
        private long delay = 1500L;

        @CommandLine.Option(names = {"-r", "--retries"}, description = "number of retries per download (default: 3)")
        private int retries = 3;

        @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help message and exit")
        private boolean helpRequested;

        @CommandLine.Option(names = {"-V", "--version"}, versionHelp = true, description = "display version info")
        private boolean versionInfoRequested;

        @Override
        public Void call() throws Exception {
            if (minLatLon != null ^ maxLatLon != null) {
                throw new IllegalArgumentException("min and max have to be specified together");
            }
            DownloadUtil downloadUtil = new DownloadUtil();
            downloadUtil.setNumRetries(retries);
            downloadUtil.setDelay(delay);
            try (OpenStreetMap osm = new OpenStreetMap(zoomLevel, outDir, downloadUtil)) {
                if (minLatLon != null && maxLatLon != null) {
                    osm.setMinMaxLatLon(minLatLon, maxLatLon);
                    osm.downloadBoundingBoxTiles(Optional.ofNullable(resumeXY));
                } else {
                    osm.downloadAllTiles(Optional.ofNullable(resumeXY));
                }
            } catch (Exception e) {
                throw e;
            }
            return null;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();

    public static final String[] TILE_SERVERS = {
            "http://a.tile.openstreetmap.org",
            "http://b.tile.openstreetmap.org",
            "http://c.tile.openstreetmap.org",};

    private String previousTileServer = TILE_SERVERS[0];
    private int zoom;
    private Point2D.Double minLatLon = new Point2D.Double(0d, 0d);
    private Point2D.Double maxLatLon = new Point2D.Double(0d, 0d);
    private File outDir;
    private DownloadUtil downloadUtil;

    public OpenStreetMap(int zoom, File outDir, DownloadUtil downloadUtil) {
        setZoom(zoom);
        this.outDir = outDir;
        this.downloadUtil = downloadUtil;
    }

    public void downloadAllTiles(Optional<Point> resume) throws IOException {
        Point min = new Point(0, 0);
        Point max = new Point((1 << zoom) - 1, (1 << zoom) - 1);
        downloadTiles(min, max, resume.orElse(min));
    }

    public void downloadBoundingBoxTiles(Optional<Point> resume) throws IOException {
        Point min = latLonToTileIndex(zoom, minLatLon.x, minLatLon.y);
        Point max = latLonToTileIndex(zoom, maxLatLon.x, maxLatLon.y);
        downloadTiles(min, max, resume.orElse(min));
    }

    public void downloadTiles(Point min, Point max, Point resume) throws IOException {
        if (!((max.y >= resume.y && resume.y >= min.y) &&
                ((max.x >= resume.x && resume.x >= min.x) ||
                        (min.x >= max.x && (max.x >= resume.x || resume.x >= min.x)))))
            throw new IllegalArgumentException("invalid min/max/resume range");

        int startX = resume.x;
        int startY = resume.y;
        int x = startX - 1;
        do {
            x = (x + 1) % (1 << zoom);
            for (int y = startY; y <= max.y; y++) {
                startY = min.y;
                URL url = UrlBuilder.fromString(getRandomTileServer())
                        .withPath(String.format("%d/%d/%d.png", zoom, x, y))
                        .toUrl();
                downloadUtil.downloadFileOriginalName(url, outDir, true);
            }
        } while (x != max.x);
    }

    private String getRandomTileServer() {
        String tileServer;
        do {
            int idx = RANDOM.nextInt(TILE_SERVERS.length);
            tileServer = TILE_SERVERS[idx];
        } while (tileServer.equals(previousTileServer));
        previousTileServer = tileServer;
        return tileServer;
    }

    public static Point latLonToTileIndex(final int zoom, final double lat, final double lon) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xtile < 0)
            xtile = 0;
        if (xtile >= (1 << zoom))
            xtile = ((1 << zoom) - 1);
        if (ytile < 0)
            ytile = 0;
        if (ytile >= (1 << zoom))
            ytile = ((1 << zoom) - 1);
        return new Point(xtile, ytile);
    }

    @Override
    public void close() throws Exception {
        downloadUtil.close();
    }

    public void setZoom(int zoom) {
        if (!(zoom >= 0 && zoom <= 19))
            throw new IllegalArgumentException("zoom level out of range (0 <= z <= 19)");
        this.zoom = zoom;
    }

    public void setMinMaxLatLon(Point2D.Double minLatLon, Point2D.Double maxLatLon) {
        this.minLatLon = minLatLon;
        this.maxLatLon = maxLatLon;
    }

}
