(ns status-im.utils.identicon
  (:require
   [status-im.native-module.core :as native-module]
   [status-im.js-dependencies :as dependencies]))

(def default-size 150)

(defn identicon
  ([hash] (identicon hash (clj->js {:background [255 255 255 255]
                                    :margin     0.24
                                    :size       default-size})))
  ([hash options]
   (native-module/identicon hash)))

(def identicon-memoized (memoize identicon))
