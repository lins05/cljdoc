(ns cljdoc.render.search
  (:require [cljdoc.render.layout :as layout]))

(defn search-form
  ([] (search-form ""))
  ([search-terms]
   [:div.w-90.mb4
    [:div#cljdoc-search {:data-initial-value search-terms}]
    [:p.bg-washed-yellow.ba.b--yellow.ph3.pv2.br2.f6.lh-copy
     "New search - please report problems and bad results in the "
     [:a.link {:href "https://github.com/cljdoc/cljdoc/issues/308"} "search improvements issue"]
     " to help us make it better!"]]))


(defn search-page [context]
  (->> [:div
        (layout/top-bar-generic)
        [:div.pt4
         [:div.mt5-ns.mw7.center.pa4.pa0-l
          [:h1.ma0
           [:span.dn "cljdoc beta"]
           [:img {:src "/cljdoc-logo-beta.svg" :alt "cljdoc logo" :width "150px"}]]
          [:p.f2-ns.f3.mv3.w-90-l.lh-copy "Library search:"]
          (search-form (-> context :request :query-params :q))]]]
       (layout/page {:title "cljdoc — documentation for Clojure/Script libraries"
                     :responsive? true})
       (str)))
