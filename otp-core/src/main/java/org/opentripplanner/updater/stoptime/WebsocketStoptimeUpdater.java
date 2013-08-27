/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.updater.stoptime;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.trippattern.TripUpdateList;
import org.opentripplanner.updater.GraphUpdater;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.GraphWriterRunnable;
import org.opentripplanner.updater.RealtimeDataSnapshotSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.websocket.DefaultWebSocketListener;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketListener;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;

/**
 * This class starts an HTTP client which opens a websocket connection to a GTFS-RT data source. A
 * callback is registered which handles incoming GTFS-RT messages as they stream in by placing a
 * GTFS-RT decoder Runnable task in the single-threaded executor for handling.
 * 
 * Usage example ('example' name is an example) in the file 'Graph.properties':
 * 
 * <pre>
 * websocket.type = websocket-stop-time-updater
 * websocket.agencyId = agency
 * websocket.url = ws://localhost:8088/tripUpdates
 * </pre>
 * 
 */
public class WebsocketStoptimeUpdater implements GraphUpdater {

    private static Logger LOG = LoggerFactory.getLogger(WebsocketStoptimeUpdater.class);

    /**
     * Parent update manager. Is used to execute graph writer runnables. 
     */
    private GraphUpdaterManager updaterManager;

    /**
     * Url with the websocket server
     */
    private String url;

    /**
     * Default agency id that is used for the trip id's in the TripUpdateLists
     */
    private String agencyId;

    @Override
    public void configure(Graph graph, Preferences preferences) throws Exception {
        // Read configuration
        url = preferences.get("url", null);
        agencyId = preferences.get("agencyId", "");
    }

    @Override
    public void setGraphUpdaterManager(GraphUpdaterManager updaterManager) {
        this.updaterManager = updaterManager;
    }

    @Override
    public void setup() throws InterruptedException, ExecutionException {
        // Create a realtime data snapshot source and wait for runnable to be executed
        updaterManager.executeBlocking(new GraphWriterRunnable() {
            @Override
            public void run(Graph graph) {
                // Only create a realtime data snapshot source if none exists already
                if (graph.getRealtimeDataSnapshotSource() == null) {
                    RealtimeDataSnapshotSource snapshotSource = new RealtimeDataSnapshotSource(graph);
                    // Add snapshot source to graph
                    graph.setRealtimeDataSnapshotSource(snapshotSource);
                }
            }
        });
    }

    @Override
    public void run() {
        // The AsyncHttpClient library uses Netty by default (it has a dependency on Netty).
        // It can also make use of Grizzly for the HTTP layer, but the Jersey-Grizzly integration
        // forces us to use a version of Grizzly that is too old to be compatible with the current
        // AsyncHttpClient. This would be done as follows:
        // AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().build();
        // AsyncHttpClient client = new AsyncHttpClient(new GrizzlyAsyncHttpProvider(config),
        // config);
        // Using Netty by default:
        AsyncHttpClient client = new AsyncHttpClient();
        WebSocketListener listener = new Listener();
        WebSocketUpgradeHandler handler = new WebSocketUpgradeHandler.Builder()
                .addWebSocketListener(listener).build();

        // TODO: add logic to reconnect after losing the connection

        @SuppressWarnings("unused")
        WebSocket socket;
        try {
            socket = client.prepareGet(url).execute(handler).get();
        } catch (ExecutionException e) {
            LOG.error("Could not connect to {}: {}", url, e.getCause().getMessage());
        } catch (Exception e) {
            LOG.error("Unknown exception when trying to connect to {}:", url, e);
        }
    }

    @Override
    public void teardown() {
    }

    /**
     * Auxiliary class to handle incoming messages via the websocket connection
     */
    private class Listener extends DefaultWebSocketListener {
        @Override
        public void onMessage(byte[] message) {
            try {
                // Decode message into TripUpdateList
                FeedMessage feed = GtfsRealtime.FeedMessage.PARSER.parseFrom(message);
                List<TripUpdateList> updates = TripUpdateList.decodeFromGtfsRealtime(feed, agencyId);

                // Handle trip updates via graph writer runnable
                TripUpdateListGraphWriterRunnable runnable = new TripUpdateListGraphWriterRunnable(updates);
                updaterManager.execute(runnable);
            } catch (InvalidProtocolBufferException e) {
                LOG.error("Could not decode gtfs-rt message:", e);
            }
        }
    }
}