;; ── 修改 undo.core ──
(ns top.kzre.krro.plugin.undo.core
  "Undo 插件注册入口，集成 hook 通知。"
  (:require
    [clojure.spec.alpha :as s]
    [top.kzre.krro.core.core :as krro]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.resource :as res]
    [top.kzre.krro.plugin.undo.internal.impl :as impl]
    [top.kzre.krro.plugin.undo.internal.project :as undo-proj]
    [top.kzre.krro.plugin.undo.protocol :as proto]))

(s/def ::fx-type keyword?)

(defn- make-fx-metadata [type data]
  (assoc data ::fx-type type))

(defrecord UndoCommand [undo-handler
                        redo-handler])

(defonce ^:private fx-command (atom {}))

(defn reg-undo-fx [id undo-handler redo-handler]
  {:pre [(keyword? id)
         (ifn? undo-handler)
         (ifn? redo-handler)]}
  (swap! fx-command assoc id (->UndoCommand undo-handler redo-handler)))

(defn- get-command [id]
  (get @fx-command id))

(defn- fx-type [node]
  (get (proto/metadata node) ::fx-type))

;; 手动操作api

(defn record-state!
  ([] (record-state! nil))
  ([data]
   (swap! proj/project
          (fn [project]
            (let [current (or (:krro.undo/undo-tree project)
                              (impl/make-undo-tree (res/encode (proj/user-data project))))]
              (assoc project :krro.undo/undo-tree
                             (proto/add-state! current (res/encode (proj/user-data project)) data)))))))

(defn record-fx!
  [type data]
  (record-state! (make-fx-metadata type data)))


(defn- restore-state [ current-node]
  (let [protected (proj/protected-data)]
    (merge (:state current-node) protected {:krro.undo/undo-tree current-node})))

(defn- undo-handler [project]
  (if-let [current (:krro.undo/undo-tree project)]
    (let [new-current (proto/undo current)]
      (if (identical? new-current current)
        project
        (let [new-project (restore-state new-current)]
          (letfn [(go [] (swap! proj/project (constantly new-project)))]
            ;; 从当前节点 undo
            (if-let [fx (fx-type current)]
              (if-let [cmd (get-command fx)]
                ((:undo-handler cmd)
                 {:old-project project
                  :new-project new-project
                  :old-node current
                  :new-node new-current}
                 go)
                (throw (ex-info "no command registered for fx-type" {:fx-type fx})))
              (go)))
          new-project)))
    project))

(defn- redo-handler [project]
  (if-let [current (:krro.undo/undo-tree project)]
    (let [new-current (proto/redo current)]
      (if (identical? new-current current)
        project
        (let [new-project (restore-state new-current)]
          (letfn [(go [] (swap! proj/project (constantly new-project)))]
            ;; 从当前节点 undo
            (if-let [fx (fx-type new-current)]
              (if-let [cmd (get-command fx)]
                ((:redo-handler cmd)
                 {:old-project project
                  :new-project new-project
                  :old-node current
                  :new-node new-current}
                 go)
                (throw (ex-info "no command registered for fx-type" {:fx-type fx})))
              (go)))
          new-project)))
    project))


(defn- branch-options []
  (when-let [current (:krro.undo/undo-tree @proj/project)]
    (let [children (proto/branches current)]
      (map-indexed (fn [idx child]
                     (str "Branch " idx ": " (pr-str (:state child))))
                   children))))

(defn- switch-branch-handler [project choice]
  (if-let [current (:krro.undo/undo-tree project)]
    (let [children (proto/branches current)
          idx (if (number? choice)
                choice
                (some (fn [[i c]] (when (= (str "Branch " i ": " (pr-str (:state c))) choice) i))
                      (map-indexed vector children)))]
      (if (and idx (<= 0 idx (dec (count children))))
        (let [new-current (proto/switch-branch current idx)]
          (if (identical? new-current current)
            project
            (restore-state new-current)))
        project))
    project))

(krro/reg-plugin!
  {:name :krro.plugin/undo
   :mount
   (fn []
     (undo-proj/polyfill-undo-tree)
     (krro/reg-command :krro.undo/undo undo-handler :description "Undo last change")
     (krro/reg-command :krro.undo/redo redo-handler :description "Redo last undone change")
     (krro/reg-command :krro.undo/undo-switch-branch switch-branch-handler
                            :description "Switch to a different undo branch"
                            :interactive [[:choice branch-options]])
     (krro/define-minor-mode
       :krro.undo/undo-tree
       :name "Undo Tree Mode"
       :keymap
       {:u :krro.undo/undo
        :r :krro.undo/redo
        }))})

