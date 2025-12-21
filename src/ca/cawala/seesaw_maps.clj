(ns ca.cawala.seesaw-maps
  (:require
   [clojure.java.io :as io]
   [org.corfield.logging4j2 :as logger]
   [seesaw.mig :as m]
   [seesaw.bind :as b]
   [seesaw.core :as s])
  (:import
   [com.formdev.flatlaf FlatLightLaf]
   [java.awt Frame Point]
   [net.miginfocom.swing MigLayout]
   [net.wirelabs.jmaps.map MapViewer]
   [net.wirelabs.jmaps.map.geo Coordinate]
   [net.wirelabs.jmaps.example.components MapPanel RoutePainter])
  (:gen-class))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn zoom-image [map-viewer scale]
  (let [base-layer (-> map-viewer .getCurrentMap .getBaseLayer)
        tile-size (* (.getOriginalTileSize base-layer) scale)
        top-left-point (.getTopLeftCornerPoint map-viewer)
        mid (Point. top-left-point)
        _ (.translate mid
                      (/ (.getWidth map-viewer) 2.0)
                      (/ (.getHeight map-viewer) 2.0))
        mid-coordinate (.pixelToLatLon base-layer mid (.getZoom map-viewer))]
    (doto base-layer (.setImageScale scale) (.setTileSize tile-size))
    (.centerOnLocation map-viewer mid-coordinate)
    (.repaint map-viewer)))

(defn zoom-tiles [map-viewer zoom]
  (let [base-layer (-> map-viewer .getCurrentMap .getBaseLayer)
        top-left-point (.getTopLeftCornerPoint map-viewer)
        mid (Point. top-left-point)
        _ (.translate mid
                      (/ (.getWidth map-viewer) 2.0)
                      (/ (.getHeight map-viewer) 2.0))
        mid-coordinate (.pixelToLatLon base-layer mid (.getZoom map-viewer))]
    (doto map-viewer (.setZoom zoom) (.centerOnLocation mid-coordinate) .repaint)))
            ;;     Point2D p = baseLayer.latLonToPixel(mouseLatLon, zoom);

            ;;     // update top left corner to new zoom
            ;;     updateTopLeftCornerPoint(evt, p);
            ;;     // update mouse point
            ;;     updateMousePoint(evt);

            ;;     // set new zoom
            ;;     mapViewer.setZoom(zoom);
            ;;     mapViewer.repaint();

(defn sliders [map-viewer]
  (let [i-zoom-slider (s/slider :id :i-zoom :orientation :vertical :major-tick-spacing 1
                                :snap-to-ticks? true :paint-track? true :value 1
                                :paint-labels? true :min 1 :max 8)
        t-zoom-slider (s/slider :id :t-zoom :orientation :vertical :major-tick-spacing 1
                                :snap-to-ticks? true :paint-track? true :value 12
                                :paint-labels? true :min 0 :max 19)]
    (b/bind i-zoom-slider (b/b-do [scale] (zoom-image map-viewer scale)))
    (b/bind t-zoom-slider (b/b-do [zoom] (zoom-tiles map-viewer zoom)))
    (m/mig-panel :constraints ["wrap" "[c]" "[][]push[][]"]
                 :items [["Image Zoom" ""]
                         [i-zoom-slider ""]
                         ["Tile Zoom" ""]
                         [t-zoom-slider "height :400:"]])))

(defn frame-content []
  (let [map-viewer (MapViewer.)
        lublin-pl (Coordinate. 22.565628, 51.247717)]
    (doto map-viewer
      (.setShowCoordinates true)
      (.setZoom 12)
      (.setHome lublin-pl)
      (.setCurrentMap (io/as-file (io/resource "OpenStreetMap.xml"))))
    (m/mig-panel :constraints ["fill" "" ""]
                 :items [[map-viewer "grow"]
                         [(sliders map-viewer) "east"]])))

(comment
  (-main)
  )

(defn show! []
  (s/invoke-later
   (try
     (let [frame (s/frame :on-close :dispose :title "Cawala Regions"
                          :icon (io/resource "babu.png")
                          :width 1200 :height 800
                          :content (frame-content))]
       (doto frame
         (.setExtendedState Frame/MAXIMIZED_BOTH)
         (s/show!)))
     (catch Exception e
       (logger/info e "Could not create UI!")))))

(defn -main [& args]
  (System/setProperty "sun.java2d.opengl" "true")
  (greet {:name (first args)})
  (FlatLightLaf/setup)
  (show!))

(comment
  (-main)
  (use 'seesaw.dev)
  (show-options (s/slider))
  (s/full-screen! frame)
  (s/full-screen! nil)
  (logger/as-marker :a :b)
  (logger/with-log-context {:id "tony"}
    (logger/info "Hello World Again"))
  (logger/with-log-tag "hungry"
    (logger/info "Hello World Again"))
  "trust")
