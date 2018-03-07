(defproject employeerepublic/deferst "0.6.0"
  :description "a simple object system builder"
  :url "http://egithub.com/employeerepublic/deferst"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :pedantic? :abort

  :exclusions [org.clojure/clojure]

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.521" :scope "provided"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [prismatic/plumbing "0.5.4"]
                 [funcool/cats "2.1.0"]
                 [funcool/promesa "1.8.1"]
                 [manifold "0.1.7-alpha6"]]

  :plugins []

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :plugins []}}

  :clean-targets ^{:protect false} ["out"
                                    :target-path])
