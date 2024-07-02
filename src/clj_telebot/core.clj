(ns telbot-upfiles.core
  (:require [clojure.core.async :refer [<!!]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.api :as t]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:gen-class))

;; internals Filesystem
(defn- walker [dirpath]
  (mapv str (filter #(.isFile %) (file-seq (io/file dirpath)))))

(defn- walker-with-id [walker-file-vector]
  (loop [i 0 v walker-file-vector acc '()]
    (if (empty? v)
      acc
      (recur (inc i) (rest v) (cons (vector (inc i) (first v)) acc)))))

(defn- write-db-file [path-to-write input-data]
  (with-open [file-w (io/writer path-to-write)]
    (doseq [line input-data]
      (let [id (first line)
            file-path (second line)]
        (.write file-w (str id ";" file-path "\n"))))))

(defn- base-name [path]
  (.getName (io/file path)))

(defn- write-user-interaction-file [path-to-write input-data]
  (with-open [file-w (io/writer path-to-write)]
    (doseq [line input-data]
      (let [id (first line)
            base-file-path (base-name (second line))]
        (.write file-w (str id ";" base-file-path "\n"))))))

;; telegram bot

(def token (:telegram-token (edn/read-string (slurp "config.edn"))))

(h/defhandler handler

  (h/command-fn "start"
                (fn [{{id :id :as chat} :chat}]
                  (println "Bot joined new chat: " chat)
                  (t/send-text token id "Bienvenido al robot lambda:)\nEscriba /help para ver ayuda")))

  (h/command-fn "help"
                (fn [{{id :id :as chat} :chat}]
                  (println "Help was requested in " chat)
                  (t/send-text token id "Estos son los comandos soportados:\n
/help: Muestra esta ayuda.
/update: Actualiza la base de datos de archivos.
/search patron: Busca el patrón entre los nombres de archivo.")))

  (h/command-fn "update"
                (fn [{{id :id :as chat} :chat}]
                  (println "Enviando archivo a " chat)
                  (do
                    (write-db-file "files-db.csv"
                                   (walker-with-id
                                    (walker "/home/functor/books")))
                    (write-user-interaction-file "user-interaction-file.csv"
                                                 (-> (walker "/home/functor/books")
                                                     walker-with-id))
                    (t/send-text token id "Actualizada la información de los archivos."))))

  (h/command-fn "search"
                (fn [{{id :id} :chat text :text :as salida}]
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/search") (= len2 2))
                      (with-open [file-reader (io/reader "user-interaction-file.csv")]
                        (doseq [line (line-seq file-reader)]
                          (if (str/includes? (str/lower-case line) (str/lower-case pattern))
                            (t/send-text token id line))))))))

  (h/message-fn
   (fn [{{id :id} :chat :as message}]
     (println "Intercepted message: " message)
     (t/send-text token id "I don't do a whole lot ... yet."))))

(def channel (p/start token handler))
(p/stop channel)

(defn main
  [& args]
  (when (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))

  (println "Starting the telbot-upfiles")
  (<!! (p/start token handler)))
