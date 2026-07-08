(ns top.kzre.krro.plugin.undo.internal.project
  "Undo 树初始化和获取工具。"
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.plugin.undo.internal.impl :as impl]))

(defn polyfill-undo-tree
  "确保项目原子中存在 undo-tree 根节点。
   若不存在，则创建一个基于当前用户数据的根节点，并注册保护键。
   返回当前 undo-tree 的根节点（UndoNode）。"
  []
  (proj/register-protected-key! :krro.undo/undo-tree)
  (if-let [tree (get-in @proj/project [:krro.undo/undo-tree])]
    tree
    (let [root (impl/make-undo-tree (proj/user-data @proj/project))]
      (swap! proj/project assoc :krro.undo/undo-tree root)
      root)))