(defproject clj-telebot "0.1.0-SNAPSHOT"
  :description "For share filesystem files using Telegram"
  :url "https://github.com/carht/clj-telebot"

  :license {:name "GPL-3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [morse               "0.2.4"]]

  :main ^:skip-aot clj-telebot.core
  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
