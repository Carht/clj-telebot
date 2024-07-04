# Clojure Telegram Bot

For sharing files from filesystem using a Telegram bot.

Para compartir archivos del sistema de archivos usando Telegram.

## Motivación

Lejos del computador y ¿necesitas un archivo de tu disco duro?. Ok, con telegram puedes tener una solución.

## Intención

La idea es compartir un directorio de archivos, buscas los archivos usando una palabra clave y posteriormente enviarlo al Telegram.


El diseño es para uso personal, amistades o para el público, similar como los FTP libres de algunos años, la diferencia
radica en que no puedes ver el filesystem sólo puedes acceder a los nombres de archivo, sin importar el anidamiento del
sistema de archivos real.

## Uso

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
