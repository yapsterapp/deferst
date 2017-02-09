(defproject employeerepublic/deferst "0.4.2"
  :description "a simple object system builder"
  :url "http://egithub.com/employeerepublic/deferst"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["releases" {:url "s3p://yapster-s3-wagon/releases/"
                              :username [:gpg :env/aws_access_key]
                              :passphrase [:gpg :env/aws_secret_key]}]
                 ["snapshots" {:url "s3p://yapster-s3-wagon/snapshots/"
                               :username [:gpg :env/aws_access_key]
                               :passphrase [:gpg :env/aws_secret_key]}]]

  :pedantic? :abort

  :exclusions [org.clojure/clojure]

  :dependencies [[org.clojure/tools.namespace "0.2.11"]
                 [prismatic/plumbing "0.5.3"]
                 [funcool/cats "2.0.0"]
                 [funcool/promesa "1.5.0"]
                 [manifold "0.1.5"]]

  :plugins [[s3-wagon-private "1.2.0"
             :exclusions [commons-codec]]
            [commons-codec "1.4"]]

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :plugins []}})
