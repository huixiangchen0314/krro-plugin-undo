(ns top.kzre.krro.plugin.undo.core-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.command :as cmd]
            [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.undo.core :as undo-core]
            [top.kzre.krro.plugin.undo.internal.protocol :as proto]))

(defn- user-data [project]
  (reduce dissoc project @proj/protected-keys))

(use-fixtures :each
              (fn [f]
                (proj/init-project!)
                (reset! plugin/plugin-registry [])            ;; 向量存储
                (reset! proj/protected-keys #{:krro/meta :krro/modes :krro/plugins})
                ;; 显式初始化 undo 插件，确保命令已注册
                (undo-core/init)
                (f)))

(deftest test-undo-plugin-init
  (let [node (:krro/undo @proj/project)]
    (is (satisfies? proto/IUndoTree node))
    (is (nil? (:parent node)))
    (is (empty? (:children node)))))

(deftest test-undo-redo-flow
  (let [initial (user-data @proj/project)]
    (proj/update-project! #(assoc % :data 42))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (= initial (user-data @proj/project)))
    (cmd/execute-command! proj/project :krro.command/redo)
    (is (= 42 (:data @proj/project)))))

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
  (let [initial-modes (get-in @proj/project [:krro/modes])]
    (proj/update-project! #(assoc % :data 42))
    (cmd/execute-command! proj/project :krro.command/record-state)
    (swap! proj/project assoc-in [:krro/modes :major] :some-other-mode)
    (cmd/execute-command! proj/project :krro.command/undo)
    (is (nil? (:data @proj/project)))
    (is (= :some-other-mode (get-in @proj/project [:krro/modes :major])))))

(deftest test-undo-branch-switching
  (proj/update-project! #(assoc % :val 1))
  (cmd/execute-command! proj/project :krro.command/record-state)
  (cmd/execute-command! proj/project :krro.command/undo)
  (proj/update-project! #(assoc % :val 2))
  (cmd/execute-command! proj/project :krro.command/record-state)

  (let [current (:krro/undo @proj/project)
        parent (:parent current)]
    (is (= 2 (count (:children parent)))))

  (is (= 2 (:val @proj/project)))
  (cmd/execute-command! proj/project :krro.command/undo)
  (cmd/execute-command! proj/project :krro.command/undo-switch-branch 0)
  (is (= 1 (:val @proj/project))))