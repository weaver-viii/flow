(ns flow.core
  (:require [flow.el :as fel]
            [flow.dom.elements :as fde]
            [flow.forms.text]
            [flow.forms.node]
            [flow.forms.primitive]
            [flow.forms.lenses]
            [flow.forms.collections]
            [flow.forms.symbols]
            [flow.forms.fn-calls]
            [flow.forms.fn-decls]
            [flow.forms.do]
            [flow.forms.if]
            [flow.forms.case]
            [flow.forms.let]
            [flow.forms.for]
            [flow.forms.sub-component])
  #+clj (:require [flow.compiler :as fc]))

(defn root [$container el]
  (fel/root $container el))

#+clj
(defmacro el [el]
  `(fel/render-el ~(fc/compile-el el &env)))

#+cljs
(defn bind-value! [lens]
  (fde/bind-value! lens))

#+cljs
(defn on [$el event listener]
  (fde/add-event-listener! $el event listener))
