(ns top.kzre.krro.plugin.undo.internal.protocol
  "Undo 插件协议：完整的编辑历史树（仿 Emacs undo-tree）。")

(defprotocol IUndoTree
  (add-state! [this project-state metadata]
    "在 current 节点后添加一个新快照作为子节点，并移动 current 到新节点。
     如果 current 已有子节点，新节点会追加到子节点列表中（保留所有分支）。")

  (undo! [this]
    "回退到当前节点的父节点。若已在根节点则无操作。返回新树。")

  (redo! [this]
    "前进到当前节点最近创建的子节点（最后一个）。若无子节点则无操作。返回新树。")

  (switch-branch! [this branch-index]
    "在当前节点的子节点中选择第 branch-index 个作为新的当前节点。
     通常用于在多个分支间切换。")

  (branches [this]
    "返回当前节点的子节点数量（即可用分支数）。")

  (current-node [this]
    "返回当前节点。")

  (root-node [this]
    "返回根节点。"))