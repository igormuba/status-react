(ns status-im.utils.gfycat.core
  (:require [status-im.utils.gfycat.animals :as animals]
            [status-im.utils.gfycat.adjectives :as adjectives]
            [status-im.native-module.core :as native-module]
            [clojure.string :as str]
            [status-im.utils.random :as rnd]
            [status-im.utils.datetime :as datetime]))

(defn- pick-random
  [gen vector]
  (str/capitalize (rnd/seeded-rand-nth gen vector)))

(defn- build-gfy
  [public-key]
  (native-module/generate-gfycat public-key))

(def unknown-gfy "Unknown")

(defn generate-gfy
  ([public-key]
   (case public-key
     nil unknown-gfy
     "0" unknown-gfy
     (build-gfy public-key)))
  ([] (generate-gfy (datetime/timestamp))))
