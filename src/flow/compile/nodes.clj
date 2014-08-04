(ns flow.compile.nodes
  (:require [flow.compile :refer [compile-identity compile-value]]
            [flow.protocols :as fp]
            [flow.util :as u]))

(alias 'fd (doto 'flow.dom create-ns))
(alias 'fh (doto 'flow.holder create-ns))

(declare compile-node)

(defn compile-attr [elem-sym [k v] {:keys [path] :as opts}]
  
  (let [compiled-value (compile-value v opts)
        deps (fp/value-deps compiled-value)

        set-attr! (u/path->sym "set" path "attr!")]
    
    (reify fp/CompiledIdentity
      (identity-deps [_] deps)

      (bindings [_] nil)

      (initial-form [_ state-sym]
        `(fd/set-attr! ~elem-sym ~k ~(fp/inline-value-form compiled-value state-sym)))

      (updated-form [_ new-state-sym updated-vars-sym]
        (u/with-updated-deps-check deps updated-vars-sym
          `(fd/set-attr! ~elem-sym ~k ~(fp/inline-value-form compiled-value new-state-sym )))))))

(defn compile-style [elem-sym [k v] {:keys [path] :as opts}]
  (let [compiled-value (compile-value v opts)
        deps (fp/value-deps compiled-value)

        set-style! (u/path->sym "set" path "style!")]
    
    (reify fp/CompiledIdentity
      (identity-deps [_] deps)

      (bindings [_] nil)

      (initial-form [_ state-sym]
        `(fd/set-style! ~elem-sym ~k ~(fp/inline-value-form compiled-value state-sym)))

      (updated-form [_ new-state-sym updated-vars-sym]
        (u/with-updated-deps-check deps updated-vars-sym
          `(fd/set-style! ~elem-sym ~k ~(fp/inline-value-form compiled-value new-state-sym)))))))

(defn compile-classes [elem-sym classes {:keys [path] :as opts}]
  (when (seq classes)
    (let [compiled-classes (map #(compile-value % opts) classes)
          deps (set (mapcat fp/value-deps compiled-classes))

          set-classes! (u/path->sym "set" path "classes!")]

      (reify fp/CompiledIdentity
        (identity-deps [_] deps)

        (bindings [_])

        (initial-form [_ state-sym]
          `(fd/set-classes! ~elem-sym (-> [~@(map #(fp/inline-value-form % state-sym) compiled-classes)]
                                          set
                                          (disj nil))))

        (updated-form [_ new-state-sym updated-vars-sym]
          (u/with-updated-deps-check deps updated-vars-sym
            `(fd/set-classes! ~elem-sym (-> [~@(map #(fp/inline-value-form % new-state-sym) compiled-classes)]
                                            set
                                            (disj nil)))))))))

(defn compile-listener [elem-sym {:keys [event listener]} {:keys [path] :as opts}]
  (let [compiled-listener (compile-identity listener opts)
        deps (fp/identity-deps compiled-listener)

        !listener-sym (u/path->sym "!" path event "listener")]
    
    (reify fp/CompiledIdentity
      (identity-deps [_] deps)

      (bindings [_]
        (concat (fp/bindings compiled-listener)
                (when (seq deps)
                  `[[~!listener-sym (atom nil)]])))

      (initial-form [_ state-sym]
        `(fd/add-listener! ~elem-sym ~event
                           ~(if (seq deps)
                              `(do
                                 (reset! ~!listener-sym ~(fp/initial-form compiled-listener state-sym))
                                 (fn [e#]
                                   ((deref ~!listener-sym) e#)))
                              
                              (fp/initial-form compiled-listener state-sym))))

      (updated-form [_ new-state-sym updated-vars-sym]
        (u/with-updated-deps-check deps updated-vars-sym
          `(reset! ~!listener-sym ~(fp/updated-form compiled-listener new-state-sym updated-vars-sym)))))))

(defn compile-child [elem-sym child {:keys [path] :as opts}]
  (let [compiled-child (compile-identity child opts)
        deps (fp/identity-deps compiled-child)
        
        !holder (u/path->sym "!" path "child")]

    (reify fp/CompiledIdentity
      (identity-deps [_] deps)

      (bindings [_]
        (concat (fp/bindings compiled-child)
                (when (seq deps)
                  [[!holder `(atom nil)]])))

      (initial-form [_ state-sym]
        (if (seq deps)
          `(let [initial-holder# (fh/new-element-holder ~(fp/initial-form compiled-child state-sym))]
             (reset! ~!holder initial-holder#)
             (fh/append-to! initial-holder# ~elem-sym))
          
          `(fd/append-child! ~elem-sym (fd/->node ~(fp/initial-form compiled-child state-sym)))))

      (updated-form [_ new-state-sym updated-vars-sym]
        (when (seq deps)
          (u/with-updated-deps-check deps updated-vars-sym
            `(let [new-child# ~(fp/updated-form compiled-child
                                                   new-state-sym
                                                   updated-vars-sym)]
               
               (reset! ~!holder (fh/swap-child! @~!holder new-child#)))))))))

(comment
  (require 'flow.parse)

  (let [syms {:state 'flow-test-state
              :new-state 'flow-test-new-state
              :updated-vars 'flow-test-updated-vars}]
    (-> (compile-identity (flow.parse/parse-form '[:div
                                               [:h1 "Show heading is:" (pr-str (<< !show-heading))]]
                                             {:elem? true
                                              :path "flow-test"})
                      syms)
        #_(render-el syms)))
  )

(defmethod compile-identity :node [{:keys [tag id style classes attrs children listeners]} {:keys [path] :as opts}]
  (let [elem-sym (u/path->sym path tag)
        compiled-attrs (map #(compile-attr elem-sym % (u/with-more-path opts [tag])) attrs)
        compiled-styles (map #(compile-style elem-sym % (u/with-more-path opts [tag])) style)
        compiled-classes (compile-classes elem-sym classes (u/with-more-path opts [tag]))
        compiled-children (map #(compile-child elem-sym %1 (u/with-more-path opts [tag (str %2)]))
                               children (range))
        compiled-listeners (map #(compile-listener elem-sym % (u/with-more-path opts [tag])) listeners)

        compiled-parts (concat compiled-children
                               compiled-attrs
                               compiled-styles
                               compiled-listeners
                               [compiled-classes])

        deps (set (mapcat fp/identity-deps compiled-parts))]

    (reify fp/CompiledIdentity
      (identity-deps [_]
        deps)

      (bindings [_]
        `[[~elem-sym (fd/new-element ~tag)]
          ~@(mapcat fp/bindings compiled-parts)])

      (initial-form [_ state-sym]
        `(do
           ~@(when id
               [`(set! (.-id ~elem-sym) ~id)])
           
           ~@(map #(fp/initial-form % state-sym) compiled-parts)

           ~elem-sym))

      (updated-form [_ new-state-sym updated-vars-sym]
        (u/with-updated-deps-check deps updated-vars-sym
          `(do
             ~@(map #(fp/updated-form % new-state-sym updated-vars-sym)
                    compiled-parts)
             
             ~elem-sym)
          elem-sym)))))
