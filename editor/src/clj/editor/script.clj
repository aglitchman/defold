(ns editor.script
  (:require [clojure.string :as string]
            [editor.protobuf :as protobuf]
            [dynamo.graph :as g]
            [editor.types :as t]
            [editor.geom :as geom]
            [editor.gl :as gl]
            [editor.gl.shader :as shader]
            [editor.gl.vertex :as vtx]
            [editor.defold-project :as project]
            [editor.scene :as scene]
            [editor.properties :as properties]
            [editor.workspace :as workspace]
            [editor.resource :as resource]
            [editor.pipeline.lua-scan :as lua-scan]
            [editor.gl.pass :as pass]
            [editor.lua :as lua])
  (:import [com.dynamo.lua.proto Lua$LuaModule]
           [editor.types Region Animation Camera Image TexturePacking Rect EngineFormatTexture AABB TextureSetAnimationFrame TextureSetAnimation TextureSet]
           [com.google.protobuf ByteString]
           [java.awt.image BufferedImage]
           [java.io PushbackReader]
           [javax.media.opengl GL GL2 GLContext GLDrawableFactory]
           [javax.media.opengl.glu GLU]
           [javax.vecmath Matrix4d Point3d]))

(set! *warn-on-reflection* true)

(def ^:private lua-code-opts {:code lua/lua})
(def ^:private go-prop-type->property-types (->> properties/go-prop-type->clj-type
                                              (map (fn [[type clj-type]]
                                                     [type (g/make-property-type (name type) clj-type)]))
                                              (into {})))

(def script-defs [{:ext "script"
                   :label "Script"
                   :icon "icons/32/Icons_12-Script-type.png"
                   :view-types [:code :default]
                   :view-opts lua-code-opts
                   :tags #{:component :overridable-properties}}
                  {:ext "render_script"
                   :label "Render Script"
                   :icon "icons/32/Icons_12-Script-type.png"
                   :view-types [:code :default]
                   :view-opts lua-code-opts
                   }
                  {:ext "gui_script"
                   :label "Gui Script"
                   :icon "icons/32/Icons_12-Script-type.png"
                   :view-types [:code :default]
                   :view-opts lua-code-opts
                   }
                  {:ext "lua"
                   :label "Lua Module"
                   :icon "icons/32/Icons_11-Script-general.png"
                   :view-types [:code :default]
                   :view-opts lua-code-opts
                   }])

(def ^:private status-errors
  {:ok nil
   :invalid-args (g/error-severe "Invalid arguments to go.property call") ; TODO: not used for now
   :invalid-value (g/error-severe "Invalid value in go.property call")})

(g/defnk produce-user-properties [_node-id script-properties]
  (let [script-props (filter (comp #{:ok} :status) script-properties)
        props (into {} (map (fn [p]
                              (let [key (:name p)
                                    type (:type p)
                                    prop (-> (select-keys p [:value])
                                           (assoc :node-id _node-id
                                                  :type (go-prop-type->property-types type)
                                                  :validation-problems (status-errors (:status p))
                                                  :edit-type {:type (properties/go-prop-type->clj-type type)}
                                                  :go-prop-type type
                                                  :read-only? true))]
                                [(keyword key) prop]))
                            script-props))
        display-order (mapv #(keyword (:name %)) script-props)]
    {:properties props
     :display-order display-order}))

(g/defnk produce-save-data [resource code]
  {:resource resource
   :content code})

(defn- lua-module->path [module]
  (str "/" (string/replace module #"\." "/") ".lua"))

(defn- lua-module->build-path [module]
  (str (lua-module->path module) "c"))

(defn- build-script [self basis resource dep-resources user-data]
  (let [user-properties (:user-properties user-data)
        properties (mapv (fn [[k v]] {:id (name k) :value (:value v) :type (:go-prop-type v)})
                         (:properties user-properties))
        modules (:modules user-data)]
    {:resource resource :content (protobuf/map->bytes Lua$LuaModule
                                                     {:source {:script (ByteString/copyFromUtf8 (:content user-data))
                                                               :filename (resource/proj-path (:resource resource))}
                                                      :modules modules
                                                      :resources (mapv lua-module->build-path modules)
                                                      :properties (properties/properties->decls properties)})}))

(g/defnk produce-build-targets [_node-id resource code user-properties modules]
  [{:node-id   _node-id
    :resource  (workspace/make-build-resource resource)
    :build-fn  build-script
    :user-data {:content code :user-properties user-properties :modules modules}
    :deps      (mapcat (fn [mod]
                         (let [path     (lua-module->path mod)
                               mod-node (project/get-resource-node (project/get-project _node-id) path)]
                           (g/node-value mod-node :build-targets))) modules)}])

(g/defnode ScriptNode
  (inherits project/ResourceNode)

  (property code g/Str (dynamic visible (g/always false)))
  (property caret-position g/Int (dynamic visible (g/always false)) (default 0))

  (output modules g/Any :cached (g/fnk [code] (lua-scan/src->modules code)))
  (output script-properties g/Any :cached (g/fnk [code] (lua-scan/src->properties code)))
  (output user-properties g/Properties :cached produce-user-properties)
  (output _properties g/Properties :cached (g/fnk [_declared-properties user-properties]
                                                  ;; TODO - fix this when corresponding graph issue has been fixed
                                                  (cond
                                                    (g/error? _declared-properties) _declared-properties
                                                    (g/error? user-properties) user-properties
                                                    true (merge-with into _declared-properties user-properties))))
  (output save-data g/Any :cached produce-save-data)
  (output build-targets g/Any :cached produce-build-targets))

(defn load-script [project self resource]
  (g/set-property self :code (slurp resource)))

(defn- register [workspace def]
  (let [args (merge def
               {:node-type ScriptNode
                :load-fn load-script})]
    (apply workspace/register-resource-type workspace (mapcat seq (seq args)))))

(defn register-resource-types [workspace]
  (for [def script-defs]
    (register workspace def)))
