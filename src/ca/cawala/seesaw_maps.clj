(ns ca.cawala.seesaw-maps
  (:require
   [clojure.java.io :as io]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [org.corfield.logging4j2 :as logger]
   [seesaw.bind :as b]
   [seesaw.tree :refer [simple-tree-model]]
   [seesaw.core :as s]
   [seesaw.mig :as m])
  (:import
   [com.formdev.flatlaf FlatLightLaf]
   [java.awt Frame Point Color BasicStroke]
   [net.wirelabs.jmaps.map MapViewer]
   [net.wirelabs.jmaps.map.painters Painter]
   [net.wirelabs.jmaps.map.geo Coordinate])
  (:gen-class))

(def db {:dbtype "sqlite" :dbname "trie.db"})
(def ds (jdbc/get-datasource db))

(defn get-region
  ([path]
   (jdbc/execute! ds (sql/format {:select [:*] :from [:region]
                                  :where [:like :materialized-path path]})
                  {:builder-fn rs/as-unqualified-kebab-maps}))
  ([lon lat depth]
   (jdbc/execute! ds (sql/format {:select [:*] :from [:region]
                                  :where [:and [:< :min-lon lon] [:< lon :max-lon]
                                          [:< :min-lat lat] [:< lat :max-lat]
                                          [:= [:length :materialized-path] depth]]})
                  {:builder-fn rs/as-unqualified-kebab-maps})))

(defn convert-to-px [map-viewer point]
  (let [zoom (.getZoom map-viewer)
        pt (-> map-viewer .getCurrentMap .getBaseLayer (.latLonToPixel point zoom))
        top-left-corner-x (.x (.getTopLeftCornerPoint map-viewer))
        top-left-corner-y (.y (.getTopLeftCornerPoint map-viewer))]
    (.setLocation pt (- (.getX pt) top-left-corner-x) (- (.getY pt) top-left-corner-y))
    pt))

(def box-painter
  (proxy [Painter] []
    (doPaint [graphics map-viewer width height]
      (let [s (.getStroke graphics)
            color (.getColor graphics)
            [sw-pt ne-pt] (map (partial convert-to-px map-viewer) (.getObjects this))]
        (doto graphics (.setColor Color/RED)
              (.setStroke (BasicStroke. 3 BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
              (.drawLine (.getX sw-pt) (.getY sw-pt) (.getX ne-pt) (.getY sw-pt))
              (.drawLine (.getX ne-pt) (.getY sw-pt) (.getX ne-pt) (.getY ne-pt))
              (.drawLine (.getX ne-pt) (.getY ne-pt) (.getX sw-pt) (.getY ne-pt))
              (.drawLine (.getX sw-pt) (.getY ne-pt) (.getX sw-pt) (.getY sw-pt))
              (.setColor color) (.setStroke s))))))

(defn zoom-image [map-viewer scale]
  (let [base-layer (-> map-viewer .getCurrentMap .getBaseLayer)
        tile-size (* (.getOriginalTileSize base-layer) scale)
        top-left-point (.getTopLeftCornerPoint map-viewer)
        mid (Point. top-left-point)
        _ (.translate mid
                      (/ (.getWidth map-viewer) 2.0)
                      (/ (.getHeight map-viewer) 2.0))
        mid-coordinate (.pixelToLatLon base-layer mid (.getZoom map-viewer))
        boxes (.getObjects box-painter)]
    (doto base-layer (.setImageScale scale) (.setTileSize tile-size))
    (.centerOnLocation map-viewer mid-coordinate)
    (when (not-empty boxes) (.setBestFit map-viewer boxes))
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

(defn nav-buttons []
  (m/mig-panel :constraints ["" "[c]"]
               :items [[(s/button :id :up :text "up") "wrap"]
                       [(s/button :id :left :text "left") "split"]
                       [(s/button :id :right :text "right") "wrap"]
                       [(s/button :id :down :text "down") "wrap"]
                       [(s/text :id :path :multi-line? false :columns 15 :halign :center) ""]]))

(defn sliders [map-viewer]
  (let [i-zoom-slider (s/slider :id :i-zoom :orientation :vertical :major-tick-spacing 1
                                :snap-to-ticks? true :paint-track? true :value 1
                                :paint-labels? true :min 1 :max 8)
        t-zoom-slider (s/slider :id :t-zoom :orientation :vertical :major-tick-spacing 1
                                :snap-to-ticks? true :paint-track? true :value 12
                                :paint-labels? true :min 0 :max 19)]
    (b/bind i-zoom-slider (b/b-do [scale] (zoom-image map-viewer scale)))
    (b/bind t-zoom-slider (b/b-do [zoom] (zoom-tiles map-viewer zoom)))
    (m/mig-panel :constraints ["" "[c] [c]" "[] [top]"]
                 :items [["Image Zoom" ""] ["Tile Zoom" "wrap"]
                         [i-zoom-slider ""] [t-zoom-slider "wrap, height :400:"]
                         [(nav-buttons) "span"]])))

(defn show-region [{:keys [min-lon max-lon min-lat max-lat] :as region} map-viewer sliders]
  (s/config! (s/select sliders [:#path]) :text (:materialized-path region))
  (let [base-layer (-> map-viewer .getCurrentMap .getBaseLayer)
        zoom (.getZoom map-viewer)
        min-map-pt (.pixelToLatLon base-layer (Point. 0 0) zoom)
        max-map-pt (.pixelToLatLon base-layer
                                   (Point. (.width (.getMapSizeInPixels map-viewer zoom))
                                           (.height (.getMapSizeInPixels map-viewer zoom)))
                                   zoom)
        bounded-min-lat (max min-lat (.getLatitude max-map-pt))
        bounded-max-lat (min max-lat (.getLatitude min-map-pt))
        _ (.setObjects box-painter [(Coordinate. min-lon bounded-min-lat)
                                    (Coordinate. max-lon bounded-max-lat)])
        boxes (.getObjects box-painter)]
    (when (not-empty boxes) (.setBestFit map-viewer boxes))
    (.repaint map-viewer)))

(defn frame-content []
  (let [map-viewer (proxy [MapViewer] []
                     (setZoom [zoom]
                       (proxy-super setZoom zoom)
                       (s/config! (s/select (or (.getParent this) this) [:#t-zoom]) :value zoom)))
        sliders (sliders map-viewer)]
    (def sliders sliders)
    (def map-viewer map-viewer)
    (doto map-viewer
      (.setShowCoordinates true)
      (.addUserOverlay box-painter)
      (.setCurrentMap (io/as-file (io/resource "OpenStreetMap.xml"))))
    (show-region (first (get-region 22.565628 51.247717 2)) map-viewer sliders)
    (s/invoke-later (s/config! (s/select sliders [:#t-zoom]) :value (.getZoom map-viewer)))
    (m/mig-panel :constraints ["fill" "" ""]
                 :items [[map-viewer "grow"]
                         [sliders "east"]])))

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
  (FlatLightLaf/setup)
  (show!))

(comment
  (-main)
  (use 'seesaw.dev)
  (show-region (first (get-region "b")) map-viewer sliders)
  (logger/as-marker :a :b)
  (logger/with-log-context {:id "tony"}
    (logger/info "Hello World Again"))
  (logger/with-log-tag "hungry"
    (logger/info "Hello World Again"))
  "trust")
