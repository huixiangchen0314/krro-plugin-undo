(ns top.kzre.krro.plugin.undo.plugin
  "定义命令模式化的 undo 插件，用于管理不可序列化数据.
  显示定义，用于替代隐式的 hook"
  (:require
   [clojure.spec.alpha :as s]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.core.plugin :refer [defplugin]]
   [top.kzre.krro.plugin.undo.protocol :as proto]))

(s/def ::undo-type keyword?)


(defrecord UndoCommand [before-undo after-undo
                        before-redo after-redo])


(defonce undo-commands (atom {}))


(defn make-handler [lifecycle]
  (let [node-select (case lifecycle
                      (:before-undo :after-undo) :old-node
                      (:before-redo :after-redo) :new-node)]
    (fn [event]
      (let [node (node-select event)
            metadata (when node (proto/metadata node))]
        (when-let [type (::undo-type metadata)]
          (let [command (get @undo-commands type)]
            (when-let [h (get command lifecycle)]
              (h metadata))))))))

(defn make-metadata [type data]
  (assoc data ::undo-type type))




(defn mount []
  (defplugin
    :krro.undo/undo-command
    (mount [undo-type before-undo after-undo before-redo after-redo]
           (swap! undo-commands undo-type
                  (->UndoCommand  before-undo after-undo
                                  before-redo after-redo)))
    (unmount [undo-type]
             (swap! undo-commands dissoc undo-type)))
  (hook/add-hook! :krro.undo/before-undo-hook (make-handler :before-undo))
  (hook/add-hook! :krro.undo/after-undo-hook (make-handler :after-undo))
  (hook/add-hook! :krro.undo/before-redo-hook (make-handler :before-redo))
  (hook/add-hook! :krro.undo/after-redo-hook (make-handler :after-redo)))