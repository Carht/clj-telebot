# Características

1. Permite buscar y compartir archivos del sistema de archivos usando Telegram.
2. Descarga de audios en formato mp3 desde youtube.
3. Guarda las imágenes, documentos y mensajes de voz enviados al canal del robot

## Uso

Usando los comandos del robot puedes subir desde tu disco duro al telegram los archivos que necesites, los
buscas usando palabras que estén presente en el título.

## Procedimiento paso a paso

1. Crea un robot con BotFather en Telegram.
2. Al ejecutar el archivo .jar, se creará el directorio /home/tu-usuario/.clj-telebot, el cuál contendrá el archivo
config.edn, donde debes agregar el token del robot y la ruta que será visible desde los comandos en Telegram.
El config.edn que se genera automáticamente explica donde debes colocar la información, usa siempre comillas dobles,
tanto para el token como el path.
4. Buscas un archivo usando una palabra clave, ejemplo: "/buscar clojure" (en el robot no usarás comillas).
   Enviará una lista de todos los archivos que contengan la palabra clojure, cada archivo tendrá un ID.
6. Posterior a la búsqueda, usas el ID del archivo para traer a Telegram desde el disco duro el que necesites.
   Ejemplo: /traer 113 y traerá el archivo que tenía el ID 113.
7. Fin.

## Importante

Comparte data que sea pública, como archivos tipo libros, no compartas información personal. Este robot **NO**
implementa ninguna medida criptográfica, al menos por el momento.

## Uso en shell
```bash
lein uberjar
java -jar clj-telebot.jar
```
