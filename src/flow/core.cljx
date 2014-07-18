(ns flow.core
  #+clj (:require [flow.expand :refer [expand-macros]]
                  [flow.parse :refer [parse-form]]
                  [flow.compile :refer [compile-form]]
                  [flow.render :refer [render-form]])
  
  #+cljs (:require flow.protocols
                   flow.dom
                   flow.forms.if
                   flow.forms.let
                   flow.forms.for
                   flow.forms.symbol
                   flow.forms.cursors))

#+clj
(defmacro el [elem]
  (let [el-sym (gensym "flow-el")]
    
    (-> (expand-macros elem &env)
        (parse-form {:elem? true
                     :path el-sym})
        (doto (->> (spit "/tmp/parsed.edn")))
        (compile-form {:state (symbol (str el-sym "-state"))
                       :old-state (symbol (str el-sym "-old-state"))
                     :new-state (symbol (str el-sym "-new-state"))
                     :updated-vars (symbol (str el-sym "-updated-vars"))
                     :dynamic-syms #{}
                     :local-syms #{}
                     :box? true})
        (doto (->> (spit "/tmp/compiled.edn")))
        (render-form)
        (doto (->> (spit "/tmp/rendered.edn"))))))

#+cljs
(defn root [$container $elem]
  (loop []
    (when-let [$child (.-firstChild $container)]
      (.removeChild $container $child)
      (recur)))
        
  (.appendChild $container $elem))

#+cljs
(defn bind-value! [cursor]
  (fd/bind-value! cursor))
