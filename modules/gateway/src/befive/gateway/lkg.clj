(ns befive.gateway.lkg
  "Last-known-good snapshots. The file is gzipped EDN with a sha256
  of the document text. Writes use a temp file, fsync and rename."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream File)
           (java.nio.channels FileChannel)
           (java.nio.file Files OpenOption StandardCopyOption
                          StandardOpenOption)
           (java.security MessageDigest)
           (java.util.zip GZIPInputStream GZIPOutputStream)))

(set! *warn-on-reflection* true)

(defn sha256-hex
  [^String text]
  (let [^MessageDigest digest (MessageDigest/getInstance "SHA-256")
        raw (.digest digest (.getBytes text "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) raw))))

(defn encode
  "EDN wrapper. `:document` is the snapshot text that was hashed."
  [document]
  (let [body (pr-str document)]
    (pr-str {:sha256 (sha256-hex body)
             :revision (:revision document)
             :document body})))

(defn decode
  "Return the snapshot, or nil when the hash does not match."
  [text]
  (let [wrapper (binding [*read-eval* false]
                  (edn/read-string {:readers {}} text))
        body (:document wrapper)
        parsed (when (string? body)
                 (binding [*read-eval* false]
                   (edn/read-string {:readers {}} body)))]
    (when (and parsed
               (= (:sha256 wrapper) (sha256-hex body)))
      parsed)))

(defn- gzip-bytes
  [^String text]
  (let [out (ByteArrayOutputStream.)]
    (with-open [gzip (GZIPOutputStream. out)]
      (.write gzip (.getBytes text "UTF-8")))
    (.toByteArray out)))

(defn- gunzip
  [^File file]
  (with-open [in (GZIPInputStream. (io/input-stream file))]
    (slurp in :encoding "UTF-8")))

(defn- revision-of
  [^File file]
  (when-let [match (re-matches #"snapshot-(\d+)\.edn\.gz" (.getName file))]
    (parse-long (second match))))

(defn- files
  [dir]
  (->> (file-seq (io/file dir))
       (filter (fn [^File file]
                 (and (.isFile file) (revision-of file))))
       (sort-by revision-of)
       vec))

(defn trim!
  "Keep the newest `keep` snapshots."
  [dir kept]
  (let [ordered (files dir)
        extra (drop-last kept ordered)]
    (doseq [^File file extra]
      (.delete file))
    true))

(defn write!
  "Write `snapshot-<rev>.edn.gz` and keep the newest three."
  [dir document]
  (let [^File root (io/file dir)
        revision (:revision document)
        _ (.mkdirs root)
        text (encode document)
        tmp (io/file root (str ".snapshot-" revision ".tmp"))
        dest (io/file root (str "snapshot-" revision ".edn.gz"))]
    (with-open [^java.io.OutputStream out (io/output-stream tmp)]
      (let [^bytes payload (gzip-bytes text)]
        (.write out payload)))
    (with-open [^FileChannel channel
                (FileChannel/open
                 (.toPath tmp)
                 (into-array OpenOption
                             [StandardOpenOption/WRITE]))]
      (.force channel true))
    (Files/move (.toPath tmp)
                (.toPath dest)
                (into-array java.nio.file.CopyOption
                            [StandardCopyOption/ATOMIC_MOVE
                             StandardCopyOption/REPLACE_EXISTING]))
    (trim! root 3)
    dest))

(defn write-safe!
  "Write LKG, swallowing IO failures. A proxy must keep serving."
  [dir document]
  (try
    (write! dir document)
    (catch Exception _
      nil)))

(defn load-latest
  "The newest snapshot whose hash matches, or nil."
  [dir]
  (let [root (io/file dir)]
    (when (.isDirectory root)
      (some (fn [^File file]
              (try
                (decode (gunzip file))
                (catch Exception _
                  nil)))
            (reverse (files root))))))
