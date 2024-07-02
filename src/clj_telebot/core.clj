(ns clj-telebot.core
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

(def token (:telegram-token (:config (edn/read-string (slurp "config.edn")))))
(def shared-path (:shared-path (:config (edn/read-string (slurp "config.edn")))))

(h/defhandler handler

  (h/command-fn "start"
                (fn [{{id :id :as chat} :chat}]
                  (println "Nueva conversación: " chat)
                  (t/send-text token id "Bienvenid@ a Kappa telegram bot :)\nLa intención de este robot es compartir archivos libremente a través de Telegram\nEscriba /ayuda para ver ayuda")))

  (h/command-fn "ayuda"
                (fn [{{id :id :as chat} :chat}]
                  (println "/ayuda ejecutado por: " chat)
                  (t/send-text token id "Estos son los comandos disponibles:\n
/ayuda: Muestra esta ayuda.
/uso: Ejemplo de uso de comandos.
/actualizar: Actualiza la base de datos de archivos.
/buscar *patrón* : Busca el patrón entre los nombres de archivo.
/traer *id-archivo* : Envía el archivo que corresponde con el ID buscado.")))

  (h/command-fn "uso"
                (fn [{{id :id} :chat text :text :as salida}]
                  (do
                    (println "/uso ejecutado por: " salida)
                    (t/send-text token id "/buscar *patrón*: El *patrón* corresponde a algún segmento de nombre de archivo.\nEjemplo: /buscar haskell, mostrará todos los archivos que contengan la palabra haskell en su nombre. \nSe enviará un ID y un nombre de archivo.\n\n
/traer *ID-archivo*: Trae a Telegram el archivo que tenga dicho ID.\nEjemplo: /traer 1, traerá el archivo (desde el disco duro) que tenga el ID 1."))))

  (h/command-fn "actualizar"
                (fn [{{id :id :as chat} :chat}]
                  (println "/actualizar ejecutado por: " chat)
                  (do
                    (write-db-file "files-db.csv"
                                   (walker-with-id
                                    (walker shared-path)))
                    (write-user-interaction-file "user-interaction-file.csv"
                                                 (-> (walker shared-path)
                                                     walker-with-id))
                    (t/send-text token id "Actualizada la información de los archivos."))))

  (h/command-fn "buscar"
                (fn [{{id :id} :chat text :text :as salida}]
                  (println "/buscar ejecutado por: " salida)
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/buscar") (= len2 2))
                      (with-open [file-reader (io/reader "user-interaction-file.csv")]
                        (doseq [line (line-seq file-reader)]
                          (if (str/includes? (str/lower-case line) (str/lower-case pattern))
                            (let [all-line (str/split line #";")
                                  file-id (first all-line)
                                  base-path (second all-line)]
                              (t/send-text token id (str "ID archivo: " file-id "\n" "Nombre: " base-path))))))))))

  (h/command-fn "traer"
                (fn [{{id :id} :chat text :text :as salida}]
                  (println "/traer ejecutado por: " salida)
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/traer") (= len2 2))
                      (with-open [file-reader (io/reader "files-db.csv")]
                        (doseq [line (line-seq file-reader)]
                          (if (= pattern (str/lower-case (first (str/split line #";"))))
                            (do
                              (t/send-text token id "Enviando el archivo, por favor espere...")
                              (t/send-document token id (io/file (second (str/split line #";"))))))))))))

  (h/message-fn
   (fn [{{id :id} :chat :as message}]
     (println "Mensaje de telegram: " message)
     (println "EOF"))))

(def channel (p/start token handler))
(p/stop channel)

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))

  (println "Starting the telbot-upfiles")
  (<!! (p/start token handler)))
