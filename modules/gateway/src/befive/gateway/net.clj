(ns befive.gateway.net
  "CIDR membership for trusted proxies. Parsing never does DNS."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

(def ^:private v6-masks
  [0x00 0x80 0xc0 0xe0 0xf0 0xf8 0xfc 0xfe 0xff])

(defn- octet?
  [text]
  (and (re-matches #"\d{1,3}" text)
       (or (= (count text) 1)
           (not (str/starts-with? text "0")))
       (let [n (parse-long text)]
         (and n (<= 0 n 255)))))

(defn- ipv4-int
  [text]
  (let [parts (str/split (str text) #"\.")]
    (when (and (= 4 (count parts)) (every? octet? parts))
      (reduce (fn [acc part]
                (+ (* acc 256) (parse-long part)))
              0
              parts))))

(defn- hex-groups
  [text]
  (cond
    (str/blank? text) []
    (or (str/starts-with? text ":")
        (str/ends-with? text ":")) nil
    :else
    (let [groups (str/split text #":")]
      (when (and (seq groups)
                 (every? #(re-matches #"[0-9A-Fa-f]{1,4}" %) groups))
        groups))))

(defn- ipv6-groups
  "Eight hextets, or nil when `text` is not an IPv6 literal."
  [text]
  (when (and (string? text) (not (str/includes? text ".")))
    (let [text (str/lower-case text)]
      (cond
        (str/includes? text ":::") nil
        (str/includes? text "::")
        (let [i (str/index-of text "::")
              left (hex-groups (subs text 0 i))
              right (hex-groups (subs text (+ i 2)))]
          (when (and left right
                     (< (+ (count left) (count right)) 8))
            (vec (concat left
                         (repeat (- 8 (count left) (count right)) "0")
                         right))))
        :else
        (let [groups (hex-groups text)]
          (when (and groups (= 8 (count groups)))
            (vec groups)))))))

(defn- ipv6-bytes
  [text]
  (when-let [groups (ipv6-groups text)]
    (byte-array
     (mapcat (fn [group]
               (let [n (Integer/parseInt group 16)]
                 [(bit-shift-right n 8) (bit-and n 0xff)]))
             groups))))

(defn- prefix-ok?
  [bits limit]
  (and bits (<= 0 bits limit)))

(defn- v4-match?
  [addr bits ip]
  (let [a (ipv4-int addr)
        b (ipv4-int ip)]
    (and a b
         (prefix-ok? bits 32)
         (or (zero? bits)
             (= (unsigned-bit-shift-right a (- 32 bits))
                (unsigned-bit-shift-right b (- 32 bits)))))))

(defn- v6-match?
  [addr bits ip]
  (let [a (ipv6-bytes addr)
        b (ipv6-bytes ip)]
    (and a b
         (prefix-ok? bits 128)
         (loop [index 0
                left bits]
           (if (or (zero? left) (>= index 16))
             true
             (let [n (min 8 left)
                   mask (nth v6-masks n)]
               (if (= (bit-and (aget ^bytes a index) mask)
                      (bit-and (aget ^bytes b index) mask))
                 (recur (inc index) (- left n))
                 false)))))))

(defn contains-cidr?
  "True when `ip` is inside `cidr`. Both must be literals."
  [cidr ip]
  (when (and (string? cidr) (string? ip))
    (let [slash (str/last-index-of cidr "/")]
      (when (and slash (pos? slash) (< slash (dec (count cidr))))
        (let [addr (subs cidr 0 slash)
              bits (parse-long (subs cidr (inc slash)))]
          (or (v4-match? addr bits ip)
              (v6-match? addr bits ip)))))))

(s/fdef contains-cidr?
  :args (s/cat :cidr any? :ip any?)
  :ret (s/nilable boolean?))

(defn trusted?
  "True when `ip` falls in any CIDR in `cidrs`."
  [cidrs ip]
  (boolean (some #(contains-cidr? % ip) cidrs)))
