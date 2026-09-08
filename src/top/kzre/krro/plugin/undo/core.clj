;; ── 修改 undo.core ──
(ns top.kzre.krro.plugin.undo.core
  "Undo 插件注册入口，集成 hook 通知。"
  (:require
    [top.kzre.krro.core.core :as krro ]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.resource :as res]
    [top.kzre.krro.plugin.undo.internal.impl :as impl]
    [top.kzre.krro.plugin.undo.internal.project :as undo-proj]
    [top.kzre.krro.plugin.undo.protocol :as proto]))

;; 手动操作api

(defn record-state!
  ([] (record-state! nil))
  ([metadata]
   (swap! proj/project
          (fn [project]
            (let [current (or (:krro.undo/undo-tree project)
                              (impl/make-undo-tree (res/encode (proj/user-data project))))]
              (assoc project :krro.undo/undo-tree
                             (proto/add-state! current (res/encode (proj/user-data project)) metadata)))))))

(defn- record-state-handler [_project]
  (record-state!))

(defn- restore-state [ current-node]
  (let [protected (proj/protected-data)]
    (merge (:state current-node) protected {:krro.undo/undo-tree current-node})))

(defn- undo-handler [project]
  (if-let [current (:krro.undo/undo-tree project)]
    (let [new-current (proto/undo current)]
      (if (identical? new-current current)
        project
        (do (hook/run-hook! :krro.undo/before-undo-hook
                            {:old-project project
                             :new-project nil
                             :old-node current
                             :new-node new-current})
            (let [new-project (restore-state new-current)]
              (swap! proj/project (constantly new-project))
              (hook/run-hook! :krro.undo/after-undo-hook
                              {:old-project project
                               :new-project new-project
                               :old-node current
                               :new-node new-current})
              new-project))))
    project))

(defn- redo-handler [project]
  (if-let [current (:krro.undo/undo-tree project)]
    (let [new-current (proto/redo current)]
      (if (identical? new-current current)
        project
        (do
          (hook/run-hook! :krro.undo/before-redo-hook
                          {:old-project project
                           :new-project nil
                           :old-node current
                           :new-node new-current})
          (let [new-project (restore-state new-current)]
            (swap! proj/project (constantly new-project))
            ;; after：在 swap! 之后触发
            (hook/run-hook! :krro.undo/after-redo-hook
                            {:old-project project
                             :new-project new-project
                             :old-node current
                             :new-node new-current})
            new-project))))
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
     (krro/reg-command :krro.undo/record-state record-state-handler :description "Save current state to undo tree")
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