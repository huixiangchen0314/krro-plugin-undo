(ns top.kzre.krro.plugin.undo.internal.impl
  "Undo 树的纯数据实现。UndoNode 自身就是树（当前节点）。"
  (:require [top.kzre.krro.plugin.undo.internal.protocol :as proto])
  (:import [java.time Instant]))

(declare make-undo-node)

(defrecord UndoNode [state parent children timestamp metadata]
  proto/IUndoTree
  (add-state! [this project-state metadata]
    ;; 在当前节点下添加一个新子节点，并返回新子节点（新的当前节点）
    (let [new-node (make-undo-node project-state)
          updated-this (assoc this :children (conj (vec (:children this)) new-node))
          new-node-with-parent (assoc new-node :parent updated-this)]
      new-node-with-parent))
  (undo! [this]
    (or (:parent this) this))
  (redo! [this]
    (or (last (:children this)) this))
  (switch-branch! [this idx]
    (or (nth (:children this) idx nil) this))
  (branches [this] (:children this))
  (current-node [this] this)
  (root-node [this]
    (loop [n this]
      (if-let [p (:parent n)] (recur p) n))))

(defn make-undo-node
  ([state] (make-undo-node state nil))
  ([state parent]
   (->UndoNode state parent [] (Instant/now) {})))

(defn make-undo-tree
  "创建一棵新树（本质上返回一个根节点）。"
  [project-state]
  (make-undo-node project-state))