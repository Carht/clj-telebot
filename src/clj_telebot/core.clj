(ns clj-telebot.core
  (:require [clojure.core.async :refer [<!!]]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.api :as t]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:gen-class))

;; internals Filesystem
(defn- walker [dirpath]
  ;; Return the filepaths recursively from `dirpath'.
  (mapv str (filter #(.isFile %) (file-seq (io/file dirpath)))))

(defn- walker-with-id [walker-file-vector]
  ;; With the input of the file list, add a counter ID.
  (loop [i 0 v walker-file-vector acc '()]
    (if (empty? v)
      acc
      (recur (inc i) (rest v) (cons (vector (inc i) (first v)) acc)))))

(defn- write-db-file [path-to-write input-data]
  ;; Create a small database (CSV) of files with id and filepath
  (with-open [file-w (io/writer path-to-write)]
    (doseq [line input-data]
      (let [id (first line)
            file-path (second line)]
        (.write file-w (str id ";" file-path "\n"))))))

(defn- base-name [path]
  ;; Return the last part of a filepath
  (.getName (io/file path)))

(defn- write-user-interaction-file [path-to-write input-data]
  ;; Create a small database (CSV) of files with id and base name.
  (with-open [file-w (io/writer path-to-write)]
    (doseq [line input-data]
      (let [id (first line)
            base-file-path (base-name (second line))]
        (.write file-w (str id ";" base-file-path "\n"))))))

;; config files

(def dir-config-path (str (System/getProperty "user.home") "/" ".clj-telebot"))
(def file-config-edn (str dir-config-path "/" "config.edn"))

(defn create-dir-config []
  ;; Create the basic files necessary for the bot.
  (if (not (.exists (io/file dir-config-path)))
    (do
      (.mkdir (io/file dir-config-path))
      (spit file-config-edn
            "{:config {:telegram-token <TOKEN-HERE-AS-STRING> :shared-path <PATH-TO-SHARED-WITH-TELEGRAM-AS-STRING>}}\n" :append true)
      (println (str "Por favor configure el archivo " file-config-edn))
      (System/exit 1))))

(create-dir-config)
;; telegram bot

(def token (:telegram-token (:config (edn/read-string (slurp file-config-edn)))))
(def shared-path (:shared-path (:config (edn/read-string (slurp file-config-edn)))))

(defn- create-dbs []
  ;; Create the small database (CSV) of files, with out a real filesystem.
  ;; files-db.csv is private and have the real path
  ;; user-interaction-file have the base name of a file and an id for relation with files-db.csv
  (do
    (write-db-file (str dir-config-path "/" "files-db.csv")
                   (walker-with-id
                    (walker shared-path)))
    (write-user-interaction-file (str dir-config-path "/" "user-interaction-file.csv")
                                 (-> (walker shared-path)
                                     walker-with-id))))

;; Youtube
(defn- download-youtube-mp3 [youtube-url]
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        output-template (str temp-dir "/%(title)s.%(ext)s")
        result (shell/sh "yt-dlp"
                         "-x"
                         "--audio-format" "mp3"
                         "--output" output-template
                         "--no-playlist"
                         youtube-url)]
    (when (not= 0 (:exit result))
      (throw (Exception. (str "Error al descargar: " (:err result)))))

    (let [output-line (first (filter #(str/includes? % "[ExtractAudio] Destination:")
                                     (str/split (:out result) #"\n")))
          filename (when output-line
                     (last (str/split output-line #": ")))]
      (when (not filename)
        (throw (Exception. "No se pudo determinar el nombre del archivo descargado")))
      filename)))

(h/defhandler handler

  (h/command-fn "start"
                (fn [{{id :id first-name :first_name :as chat} :chat}]
                  (println "Nueva conversación: " chat)
                  (t/send-text token id (str  "Bienvenid@ " first-name " al robot de Telegram :)\nLa intención de este robot es compartir archivos a través de Telegram\nEscriba /ayuda para ver ayuda"))))

  (h/command-fn "ayuda"
                (fn [{{id :id :as chat} :chat}]
                  (println "/ayuda ejecutado por: " chat)
                  (t/send-text token id "Estos son los comandos disponibles:\n
/ayuda: Muestra esta ayuda.
/uso: Ejemplo de uso de comandos.
/actualizar: Actualiza la base de datos de archivos.
/buscar *patrón* : Busca el patrón entre los nombres de archivo.
/traer *id-archivo* : Envía el archivo que corresponde con el ID buscado.
/mp3 *youtube-url*: Descarga el audio en formato mp3.")))

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
                    (create-dbs)
                    (t/send-text token id "Actualizada la información de los archivos."))))

  (h/command-fn "mp3"
                (fn [{{id :id} :chat text :text :as salida}]
                  (do
                    (println "/mp3 usado por: " salida))
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/mp3") (= len2 2))
                      (cond
                        (or (nil? pattern) (not (str/includes? pattern "youtu"))) (t/send-text token id "Ingrese una url válida.")
                        (str/includes? pattern "www.youtube.com/results") (t/send-text token id "Ingrese una URL directa Ejemplo:\nhttps://www.youtube.com/watch?v=WfX4OoJhAbg")
                        :else
                        (try
                          (t/send-text token id "Descargando audio...")
                          (let [audio-file (download-youtube-mp3 pattern)]
                            (t/send-document token id (io/file audio-file))
                            (io/delete-file audio-file)
                            (t/send-text token id "¡Audio enviado!"))
                          (catch Exception e
                            (t/send-text token id (str "Error: " (.getMessage e))))))))))

  (h/command-fn "buscar"
                (fn [{{id :id} :chat text :text :as salida}]
                  (do
                    (create-dbs)
                    (println "/buscar ejecutado por: " salida))
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/buscar") (= len2 2))
                      (with-open [file-reader (io/reader (str dir-config-path "/" "user-interaction-file.csv"))]
                        (doseq [line (line-seq file-reader)]
                          (if (str/includes? (str/lower-case line) (str/lower-case pattern))
                            (let [all-line (str/split line #";")
                                  file-id (first all-line)
                                  base-path (second all-line)]
                              (t/send-text token id (str "ID archivo: " file-id "\n" "Nombre: " base-path))))))))))

  (h/command-fn "traer"
                (fn [{{id :id} :chat text :text :as salida}]
                  (do
                    (create-dbs)
                    (println "/traer ejecutado por: " salida))
                  (println "/traer ejecutado por: " salida)
                  (let [text-splited (str/split text #" ")
                        command (first text-splited)
                        pattern (second text-splited)
                        len2 (count (take 2 text-splited))]
                    (if (and (= command "/traer") (= len2 2))
                      (with-open [file-reader (io/reader (str dir-config-path "/" "files-db.csv"))]
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
  (println "Starting the clj-telebot")
  (<!! (p/start token handler)))
