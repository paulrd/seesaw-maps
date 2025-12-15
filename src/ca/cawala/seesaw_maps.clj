(ns ca.cawala.seesaw-maps
  (:require
   [clojure.java.io :as io]
   [org.corfield.logging4j2 :as logger]
   [seesaw.mig :as m]
   [seesaw.core :as s])
  (:import
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

(defn frame-content []
  (let [map-viewer (proxy [MapViewer] []
                     #_(paintComponent [^java.awt.Graphics2D g]
                       (.scale g 4 4)
                       (proxy-super paintComponent g)))
        ;;map-viewer (MapViewer.)
        lublin-pl (Coordinate. 22.565628, 51.247717)]
    (def map-viewer map-viewer)
    (doto map-viewer
      (.setShowCoordinates true)
      (.setZoom 12)
      (.setHome lublin-pl)
      (.setCurrentMap (io/as-file (io/resource "OpenStreetMap.xml"))))
    (m/mig-panel :constraints ["" "[grow]" "[grow]"]
                 :items [[map-viewer "grow"]])))

(defn show! []
  (s/invoke-later
   (s/native!)
   (try
     (let [frame (s/frame :on-close :dispose :title "Cawala Regions"
                          :icon (io/resource "babu.png")
                          :width 1200 :height 800
                          :content (frame-content))]
       (def frame frame)
       (doto frame
         (.setExtendedState Frame/MAXIMIZED_BOTH)
         (s/show!)))
     (catch Exception e
       (logger/info e "Could not create UI!")))))

(defn -main [& args]
  (System/setProperty "sun.java2d.opengl" "true")
  (greet {:name (first args)})
  (show!))

(comment
  (-main)
  (s/invoke-later
   (let [base-layer (-> map-viewer .getCurrentMap .getBaseLayer)
         scale (float 4)
         tile-size (* (.getOriginalTileSize base-layer) scale)
         top-left-point (.getTopLeftCornerPoint map-viewer)
         mid (Point. top-left-point)
         _ (.translate mid
                       (/ (.getWidth map-viewer) 2.0) (/ (.getHeight map-viewer) 2.0))
         mid-coordinate (.pixelToLatLon base-layer mid) (.getZoom map-viewer)]
     (doto base-layer (.setImageScale scale) (.setTileSize tile-size))
     (.centerOnLocation map-viewer mid-coordinate)
     (.repaint map-viewer)
     #_(s/pack! frame)))

  (s/invoke-later (-> map-viewer .getTopLeftCornerPoint (.translate 100 100)))
  (.removeComponentListener map-viewer (first (.getComponentListeners map-viewer)))
  (count (.getComponentListeners map-viewer))
  (println (.getTopLeftCornerPoint map-viewer))
  (s/invoke-later (.repaint frame))
  (s/invoke-later (.repaint map-viewer))
  (s/full-screen! frame)
  (s/full-screen! nil)
  (show!)
  (.exists (io/as-file "OpenStreetMap.xml"))
  (.exists (io/as-file "babu.png"))
  (.exists (io/as-file (io/resource "OpenStreetMap.xml")))
  (logger/as-marker :a :b)
  (logger/with-log-context {:id "tony"}
    (logger/info "Hello World Again"))
  (logger/with-log-tag "hungry"
    (logger/info "Hello World Again"))
  "trust")
