(ns status-im.ui.screens.multiaccounts.recover.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.ui.components.text-input.view :as text-input]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.multiaccounts.recover.core :as multiaccounts.recover]
            [status-im.hardwallet.core :as hardwallet]
            [status-im.i18n :as i18n]
            [status-im.ui.components.styles :as components.styles]
            [status-im.utils.config :as config]
            [status-im.ui.components.common.common :as components.common]
            [status-im.utils.security :as security]
            [status-im.ui.components.colors :as colors]
            [status-im.utils.gfycat.core :as gfy]
            [status-im.utils.identicon :as identicon]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.screens.intro.views :as intro.views]
            [status-im.utils.utils :as utils]
            [status-im.constants :as constants]
            [status-im.ui.components.list-item.views :as list-item]
            [status-im.utils.platform :as platform]))

(defn bottom-sheet-view []
  [react/view {:flex 1 :flex-direction :row}
   [react/view {:flex 1}
    [list-item/list-item
     {:theme               :action
      :title               :t/enter-seed-phrase
      :accessibility-label :enter-seed-phrase-button
      :icon                :main-icons/text
      :on-press            #(re-frame/dispatch [::multiaccounts.recover/enter-phrase-pressed])}]
    (when (and config/hardwallet-enabled?
               platform/android?)
      [list-item/list-item
       {:theme               :action
        :title               :t/recover-with-keycard
        :disabled?           (not config/hardwallet-enabled?)
        :accessibility-label :recover-with-keycard-button
        :icon                :main-icons/keycard-logo
        :on-press            #(re-frame/dispatch [::hardwallet/recover-with-keycard-pressed])}])]])

(def bottom-sheet
  {:content        bottom-sheet-view
   :content-height (if platform/android? 130 65)})

(defview enter-phrase []
  (letsubs [wizard-state [:intro-wizard]]
    [intro.views/intro-wizard wizard-state]))

(defview success []
  (letsubs [wizard-state [:intro-wizard]]
    [intro.views/intro-wizard wizard-state]))

(defview select-storage []
  (letsubs [wizard-state [:intro-wizard]]
    [intro.views/intro-wizard wizard-state]))

(defview enter-password []
  (letsubs [wizard-state [:intro-wizard]]
    [intro.views/intro-wizard wizard-state]))

(defview confirm-password []
  (letsubs [wizard-state [:intro-wizard]]
    [intro.views/intro-wizard wizard-state]))

