(ns status-im.multiaccounts.recover.core
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.mnemonic :as mnemonic]
            [status-im.i18n :as i18n]
            [status-im.multiaccounts.create.core :as multiaccounts.create]
            [status-im.multiaccounts.db :as db]
            [status-im.native-module.core :as status]
            [status-im.react-native.js-dependencies :as js-dependencies]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.fx :as fx]
            [status-im.utils.security :as security]
            [status-im.utils.types :as types]
            [status-im.utils.platform :as platform]))

(defn check-phrase-warnings [recovery-phrase]
  (cond (string/blank? recovery-phrase) :required-field
        (not (mnemonic/valid-words? recovery-phrase)) :recovery-phrase-invalid
        (not (mnemonic/status-generated-phrase? recovery-phrase)) :recovery-phrase-unknown-words))

(fx/defn set-phrase
  {:events [:multiaccounts.recover/passphrase-input-changed]}
  [{:keys [db]} masked-recovery-phrase]
  (let [recovery-phrase (security/safe-unmask-data masked-recovery-phrase)]
    (fx/merge
     {:db (update db :intro-wizard assoc
                  :passphrase (string/lower-case recovery-phrase)
                  :passphrase-error nil
                  :next-button-disabled? (or (empty? recovery-phrase)
                                             (not (mnemonic/valid-length? recovery-phrase))))})))

(fx/defn validate-phrase-for-warnings
  [{:keys [db]}]
  (let [recovery-phrase (get-in db [:intro-wizard :passphrase])]
    {:db (update db :intro-wizard assoc
                 :passphrase-error (check-phrase-warnings recovery-phrase))}))

(fx/defn on-store-multiaccount-success
  {:events       [::store-multiaccount-success]
   :interceptors [(re-frame/inject-cofx :random-guid-generator)
                  (re-frame/inject-cofx ::multiaccounts.create/get-signing-phrase)]}
  [{:keys [db] :as cofx} password]
  (let [multiaccount (get-in db [:intro-wizard :root-key])
        multiaccount-address (-> (:address multiaccount)
                                 (string/lower-case)
                                 (string/replace-first "0x" ""))
        keycard-multiaccount? (boolean (get-in db [:multiaccounts/multiaccounts multiaccount-address :keycard-key-uid]))]
    (if keycard-multiaccount?
      ;; trying to recover multiaccount created with keycard
      {:db        (-> db
                      (update :intro-wizard assoc
                              :processing? false
                              :passphrase-error :recover-keycard-multiaccount-not-supported)
                      (update :intro-wizard dissoc
                              :passphrase-valid?))}
      (let [multiaccount (assoc multiaccount :derived (get-in db [:intro-wizard :derived]))]
        (multiaccounts.create/on-multiaccount-created cofx
                                                      multiaccount
                                                      password
                                                      {:seed-backed-up? true})))))

(fx/defn store-multiaccount
  {:events [::recover-multiaccount-confirmed]}
  [{:keys [db] :as cofx}]
  (let [password (get-in db [:intro-wizard :key-code])
        {:keys [passphrase root-key]} (:intro-wizard db)
        {:keys [id address]} root-key
        callback #(re-frame/dispatch [::store-multiaccount-success password])
        hashed-password (ethereum/sha3 (security/safe-unmask-data password))]
    {:db (assoc-in db [:intro-wizard :processing?] true)
     ::multiaccounts.create/store-multiaccount [id address hashed-password callback]}))

(fx/defn recover-multiaccount-with-checks
  {:events [::sign-in-button-pressed]}
  [{:keys [db] :as cofx}]
  (let [{:keys [passphrase processing?]} (:intro-wizard db)]
    (when-not processing?
      (if (mnemonic/status-generated-phrase? passphrase)
        (store-multiaccount cofx)
        {:ui/show-confirmation
         {:title               (i18n/label :recovery-typo-dialog-title)
          :content             (i18n/label :recovery-typo-dialog-description)
          :confirm-button-text (i18n/label :recovery-confirm-phrase)
          :on-accept           #(re-frame/dispatch [::recover-multiaccount-confirmed])}}))))

(re-frame/reg-fx
 ::import-multiaccount
 (fn [{:keys [passphrase password]}]
   (status/multiaccount-import-mnemonic
    passphrase
    password
    (fn [result]
      (let [{:keys [id] :as root-data} (types/json->clj result)]
        (status-im.native-module.core/multiaccount-derive-addresses
         id
         [constants/path-default-wallet constants/path-whisper]
         (fn [result]
           (let [derived-data (types/json->clj result)]
             (re-frame/dispatch [::import-multiaccount-success
                                 root-data derived-data])))))))))

(fx/defn on-import-multiaccount-success
  {:events [::import-multiaccount-success]}
  [{:keys [db] :as cofx} root-data derived-data]
  (fx/merge cofx
            {:db (update db :intro-wizard
                         assoc :root-key root-data
                         :derived derived-data
                         :step :recovery-success
                         :forward-action :multiaccounts.recover/re-encrypt-pressed)}
            (navigation/navigate-to-cofx :recover-multiaccount-success nil)))

(fx/defn enter-phrase-pressed
  {:events [::enter-phrase-pressed]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db       (assoc db
                              :intro-wizard {:step :enter-phrase
                                             :next-button-disabled? true
                                             :weak-password? true
                                             :encrypt-with-password? true
                                             :first-time-setup? false
                                             :back-action :multiaccounts.recover/cancel-pressed
                                             :forward-action :multiaccounts.recover/enter-phrase-next-pressed})
             :dispatch [:bottom-sheet/hide-sheet]}
            (navigation/navigate-to-cofx :recover-multiaccount-enter-phrase nil)))

(fx/defn proceed-to-import-mnemonic
  {:events [:multiaccounts.recover/enter-phrase-input-submitted :multiaccounts.recover/enter-phrase-next-pressed]}
  [{:keys [db] :as cofx}]
  (let [{:keys [password passphrase]} (:intro-wizard db)]
    (when (mnemonic/valid-length? passphrase)
      {::import-multiaccount {:passphrase passphrase
                              :password password}})))

(fx/defn dec-step
  {:events [:multiaccounts.recover/dec-step]}
  [{:keys [db] :as cofx}]
  (let [step (get-in db [:intro-wizard :step])]
    (when-not (= step :enter-phrase)
      {:db (update db :intro-wizard assoc :step
                   (case step
                     :recovery-success :enter-phrase
                     :select-key-storage :recovery-success
                     :create-code :select-key-storage
                     :confirm-code :create-code)
                   :confirm-failure? false
                   :key-code nil
                   :weak-password? true)})))

(defn back-button-listener []
  (re-frame/dispatch [:multiaccounts.recover/cancel-pressed])
  ;; Return true so that RN considers back button press handled
  true)

;; This counter is used
;; to fix a problem when navigating
;; back in a wizard invokes on-will-focus first,
;; and then on-will-blur, which might cause the listener
;; to be removed altogether
(def add-counter (atom 0))

(defn add-back-listener []
  (swap! add-counter inc)
  (when (= 1 @add-counter)
    (.addEventListener js-dependencies/back-handler "hardwareBackPress"
                       back-button-listener)))

(defn remove-back-listener []
  (swap! add-counter dec)
  (when (= 0 @add-counter)
    (.removeEventListener js-dependencies/back-handler "hardwareBackPress"
                          back-button-listener)))

(fx/defn cancel-pressed
  {:events [:multiaccounts.recover/cancel-pressed]}
  [{:keys [db] :as cofx}]
  ;; Workaround for multiple Cancel button clicks
  ;; that can break navigation tree
  (when-not (#{:multiaccounts :login} (:view-id db))
    (fx/merge cofx
              dec-step
              navigation/navigate-back)))

(fx/defn select-storage-next-pressed
  {:events       [:multiaccounts.recover/select-storage-next-pressed]
   :interceptors [(re-frame/inject-cofx :random-guid-generator)]}
  [{:keys [db] :as cofx}]
  (let [storage-type (get-in db [:intro-wizard :selected-storage-type])]
    (if (= storage-type :advanced)
      ;;TODO: fix circular dependency to remove dispatch here
      {:dispatch [:recovery.ui/keycard-option-pressed]})
    (fx/merge cofx
              {:db (update db :intro-wizard assoc :step :create-code
                           :forward-action :multiaccounts.recover/enter-password-next-pressed)}
              (navigation/navigate-to-cofx :recover-multiaccount-enter-password nil))))

(fx/defn re-encrypt-pressed
  {:events [:multiaccounts.recover/re-encrypt-pressed]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (update db :intro-wizard
                         assoc :step :select-key-storage
                         :forward-action :multiaccounts.recover/select-storage-next-pressed
                         :selected-storage-type :default)}
            (if platform/android?
              (navigation/navigate-to-cofx :recover-multiaccount-select-storage nil)
              (select-storage-next-pressed))))

(fx/defn proceed-to-password-confirm
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db  (update db :intro-wizard assoc :step :confirm-code
                          :forward-action :multiaccounts.recover/confirm-password-next-pressed)}
            (navigation/navigate-to-cofx :recover-multiaccount-confirm-password nil)))

(fx/defn enter-password-next-button-pressed
  {:events [:multiaccounts.recover/enter-password-next-pressed]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (assoc-in db [:intro-wizard :stored-key-code] (get-in db [:intro-wizard :key-code]))}
            #_(validate-password)
            (proceed-to-password-confirm)))

(fx/defn confirm-password-next-button-pressed
  {:events [:multiaccounts.recover/confirm-password-next-pressed]
   :interceptors [(re-frame/inject-cofx :random-guid-generator)]}
  [{:keys [db] :as cofx}]
  (let [{:keys [key-code stored-key-code]} (:intro-wizard db)]
    (if (= key-code stored-key-code)
      (fx/merge cofx
                (store-multiaccount)
                (navigation/navigate-to-cofx :keycard-welcome nil))
      {:db (assoc-in db [:intro-wizard :confirm-failure?] true)})))

(fx/defn count-words
  [{:keys [db]}]
  (let [passphrase (get-in db [:intro-wizard :passphrase])]
    {:db (assoc-in db [:intro-wizard :passphrase-word-count]
                   (mnemonic/words-count passphrase))}))

(fx/defn run-validation
  [{:keys [db] :as cofx}]
  (let [passphrase (get-in db [:intro-wizard :passphrase])]
    (when (= (last passphrase) " ")
      (fx/merge cofx
                (validate-phrase-for-warnings)))))

(fx/defn enter-phrase-input-changed
  {:events [:multiaccounts.recover/enter-phrase-input-changed]}
  [cofx input]
  (fx/merge cofx
            (set-phrase input)
            (count-words)
            (run-validation)))
