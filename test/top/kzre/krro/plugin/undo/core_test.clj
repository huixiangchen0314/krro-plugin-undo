(ns top.kzre.krro.plugin.undo.core-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.command :as cmd]
            [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.undo.core :as undo]
            [top.kzre.krro.plugin.undo.internal.protocol :as proto]))

(defn- user-data [project]
  (reduce dissoc project @proj/protected-keys))

(use-fixtures :each
              (fn [f]
                (proj/init-project!)
                (reset! plugin/plugin-registry {})
                (when-let [v (resolve 'top.kzre.krro.core.command/command-registry)]
                  (reset! @v {}))
                (plugin/register-plugin! undo/plugin)
                (f)))

(deftest test-undo-plugin-init
  (let [node (:krro/undo @proj/project)]
    (is (satisfies? proto/IUndoTree node))
    (is (nil? (:parent node)))
    (is (empty? (:children node)))))

(deftest test-undo-redo-flow
  (let [initial (user-data @proj/project)]          ;; 仅用户数据
    (proj/update-project! #(assoc % :data 42))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (= initial (user-data @proj/project)))      ;; 比较用户数据部分
    (cmd/execute-command! proj/project :krro.command/redo)
    (is (= 42 (:data @proj/project)))))              ;; redo 后用户数据有 :data

(deftest test-multiple-undos
  (let [initial (user-data @proj/project)]
    (proj/update-project! #(assoc % :x 1))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (proj/update-project! #(assoc % :x 2))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (proj/update-project! #(assoc % :x 3))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (is (= 3 (:x @proj/project)))
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (= 2 (:x @proj/project)))
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (= 1 (:x @proj/project)))
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (= initial (user-data @proj/project)))))

(deftest test-undo-preserves-kernel-state
  ;; 验证内核状态不会被 undo 改变
  (let [initial-modes (get-in @proj/project [:krro/modes])]
    (proj/update-project! #(assoc % :data 42))
    (cmd/execute-command! proj/project :krro.command/record-state)
    ;; 模拟模式切换（直接修改内核状态）
    (swap! proj/project assoc-in [:krro/modes :major] :some-other-mode)
    (cmd/execute-command! proj/project :krro.command/undo)
    ;; undo 应只回滚用户数据，内核状态保持不变
    (is (nil? (:data @proj/project)))
    (is (= :some-other-mode (get-in @proj/project [:krro/modes :major])))))

(deftest test-undo-branch-switching
  (proj/update-project! #(assoc % :val 1))
  (cmd/execute-command! proj/project :krro.command/record-state)   ; state1
  (cmd/execute-command! proj/project :krro.command/undo)           ; 回 state0
  (proj/update-project! #(assoc % :val 2))
  (cmd/execute-command! proj/project :krro.command/record-state)   ; state2

  ;; 当前节点是 state2，父节点是 state0，应有 2 个子分支
  (let [current (:krro/undo @proj/project)
        parent (:parent current)]
    (is (= 2 (count (:children parent)))))

  (is (= 2 (:val @proj/project)))
  (cmd/execute-command! proj/project :krro.command/undo)           ; 回 state0
  (cmd/execute-command! proj/project :krro.command/undo-switch-branch 0) ; 到 state1
  (is (= 1 (:val @proj/project))))