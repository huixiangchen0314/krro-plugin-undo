(ns top.kzre.krro.plugin.undo.internal.impl
  "Undo 树的纯数据实现。UndoNode 自身就是树（当前节点）。"
  (:require [top.kzre.krro.plugin.undo.protocol :as proto])
  (:import [java.time Instant]))

(declare make-undo-node)

(defrecord UndoNode [state parent children-atom timestamp metadata]
  proto/IUndoTree
  (add-state! [this project-state metadata']
    ;; 创建新节点并加入当前节点的子列表，返回新节点作为新 current
    (let [new-node (assoc (make-undo-node project-state)
                     :parent this
                     :metadata metadata')]
      (swap! children-atom conj new-node)
      new-node))
  (undo [this]
    (or parent this))
  (redo [this]
    (or (last @children-atom) this))
  (switch-branch [this idx]
    (or (nth @children-atom idx nil) this))
  (branches [_this] @children-atom)
  (current-node [this] this)
  (root-node [this]
    (loop [n this]
      (if-let [p (:parent n)] (recur p) n)))
  (metadata [_this] metadata))

(defn make-undo-node
  ([state] (make-undo-node state nil))
  ([state parent]
   (->UndoNode state parent (atom []) (Instant/now) {})))

(defn make-undo-tree
  "创建一棵新树（本质上返回一个根节点）。"
  [project-state]
  (make-undo-node project-state))