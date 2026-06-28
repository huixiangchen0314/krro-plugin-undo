(ns top.kzre.krro.plugin.undo.core
  "Undo 插件注册入口。"
  (:require
   [top.kzre.krro.core.command :as cmd]
   [top.kzre.krro.core.plugin :as plugin]
   [top.kzre.krro.core.project :as proj]
   [top.kzre.krro.plugin.undo.internal.impl :as impl]
   [top.kzre.krro.plugin.undo.internal.protocol :as proto]))


(defn- restore-state [project current-node]
  (let [protected (select-keys project @proj/protected-keys)]
    (merge (:state current-node) protected {:krro/undo current-node})))

(defn- undo-handler [project]
  (if-let [current (:krro/undo project)]
    (let [new-current (proto/undo! current)]
      (if (identical? new-current current)
        project
        (restore-state project new-current)))
    project))

(defn- redo-handler [project]
  (if-let [current (:krro/undo project)]
    (let [new-current (proto/redo! current)]
      (if (identical? new-current current)
        project
        (restore-state project new-current)))
    project))

(defn- record-state-handler [project]
  (let [current (or (:krro/undo project)
                    (impl/make-undo-tree (proj/user-data project)))]
    (assoc project :krro/undo (proto/add-state! current (proj/user-data project) {:command :manual}))))

(defn- branch-options []
  (when-let [current (:krro/undo @proj/project)]
    (let [children (proto/branches current)]
      (map-indexed (fn [idx child]
                     (str "Branch " idx ": " (pr-str (:state child))))
                   children))))

(defn- switch-branch-handler [project choice]
  (if-let [current (:krro/undo project)]
    (let [children (proto/branches current)
          idx (if (number? choice)
                choice
                (some (fn [[i c]] (when (= (str "Branch " i ": " (pr-str (:state c))) choice) i))
                      (map-indexed vector children)))]
      (if (and idx (<= 0 idx (dec (count children))))
        (let [new-current (proto/switch-branch! current idx)]
          (if (identical? new-current current)
            project
            (restore-state project new-current)))
        project))
    project))

(defn init []
  (proj/register-protected-key! :krro/undo)
  (proj/update-project!
    (fn [p]
      (if (:krro/undo p)
        p
        (assoc p :krro/undo (impl/make-undo-tree (proj/user-data p))))))
  (cmd/register-command! :krro.command/undo undo-handler :description "Undo last change")
  (cmd/register-command! :krro.command/redo redo-handler :description "Redo last undone change")
  (cmd/register-command! :krro.command/record-state record-state-handler :description "Save current state to undo tree")
  (cmd/register-command! :krro.command/undo-switch-branch switch-branch-handler
                         :description "Switch to a different undo branch"
                         :interactive [[:choice branch-options]]))

(plugin/register-plugin! {:name :krro.plugin/undo :init init})