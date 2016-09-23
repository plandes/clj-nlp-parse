(ns ^{:doc "Feature utility functions.  See [[zensols.nlparse.feature.lang]]."
      :author "Paul Landes"}
    zensols.nlparse.locale
  (:import (java.util Locale)
           (com.neovisionaries.i18n LanguageCode))
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.data.csv :as csv]
            [clojure.tools.logging :as log]
            [clojure.set :refer (difference)])
  (:require [zensols.actioncli.dynamic :refer (defa- undef)]))

(def ^:private unicodes-inst (atom nil))
(def ^:private unicode-to-locale-inst (atom nil))
(def ^:private unicode-to-locale-vals-inst (atom nil))
(defa- name-to-locale-fn-inst)

(defn set-name-to-locale-fn
  "Set the function to use when mapping a language name to a
  `java.util.Locale`.  The default uses [[name-to-locale]]."
  [name-to-locale-fn]
  (reset! name-to-locale-fn-inst name-to-locale-fn))

(defn locale-to-lang-code
  "Return the two letter language code for a Locale"
  [^Locale loc]
  (.getLanguage loc))

(defn lang-code-to-locale
  "Return a Locale for a two letter language code."
  [^String lang-code]
  (or (Locale/forLanguageTag lang-code)
      (Locale. lang-code)))

(defn name-by-locale
  "Return a map of language name to two letter language code.  This iterates
  over `java.util.Locale/getAvailableLocales` using `.getDisplayName`.
  See [[java.util.Locale/getAvailableLocales]]."
  []
  (->> (Locale/getAvailableLocales)
       (map (fn [lc]
              (let [name (.getDisplayName lc)]
                (if (> (count name) 0)
                  {(locale-to-lang-code lc) lc}))))
       (remove nil?)
       (apply merge)
       ((fn [m]
          (let [lcs (vals m)]
            (zipmap (map #(.getDisplayName %) lcs) lcs))))))

(defn name-to-locale
  "Like [[name-by-locale]] but set difference out **remove-langs** and use the
  mapping in **locale-map** when it exists.  Each key (also used for lookup in
  **remove-langs** and **locale-map**) subtracts out the parenthetical langauge
  name (i.e. *Arabic (Lebanon)* -> *Arabic*).

  The value (language code) uses
  `com.neovisionaries.i18n.LanguageCode/findByName`."
  ([]
   (name-to-locale #{} {}))
  ([remove-langs lang-map]
   (let [locale-map (name-by-locale)]
     (->> (Locale/getAvailableLocales)
          (map #(.getDisplayName %))
          (map (fn [lang-name]
                 (when-not (contains? remove-langs lang-name)
                   (let [lang-name (second (re-find #"^(.+?)(?: (.+))?$" lang-name))
                         loc (or (get lang-map lang-name)
                                 (get locale-map lang-name)
                                 (->> (format ".*%s.*" lang-name)
                                      LanguageCode/findByName
                                      first
                                      (#(if % (.toLocale %)))))]
                     (log/infof "%s => %s%s" lang-name loc
                                (if loc "" " (skipping)"))
                     {lang-name loc}))))
          (remove (fn [m] (->> m vals first nil?)))
          (apply merge)))))

(defn- print-unicode-range
  ([]
   (print-unicode-range (read-string (str "0x" "0600"))
                        (read-string (str "0x" "06FF"))))
  ([start end]
   (->> (range start end)
        (map #(Character/toString (char %)))
        (apply str)
        println)))

(defn- unicode-ranges
  "Return a map with values each with `:name` of a Unicode
  name (i.e. cyrillic) with value `:range` having a duple of the inclusive
  range of Unicode character."
  []
  (with-open [reader (io/reader (io/resource "unicode-ranges.csv"))]
    (->> reader
         csv/read-csv
         (map (fn [[start end unicode-name]]
                {unicode-name {:name unicode-name
                               :range [(read-string (str "0x" start))
                                       (read-string (str "0x" end))]}}))
         (apply merge)
         doall)))

(defn- create-unicodes
  "Return a map of Unicode ranges with associcated locale and name information.
  For keys, see **:name** of [[unicode-for-char]]."
  []
  (letfn [(unicodes-for-regex [uranges regex]
            (->> uranges
                 (map (fn [[name _]]
                        (if (re-find regex name)
                          name)))
                 (remove nil?)
                 set))
          (loc-to-name [loc]
            (format "lang-%s" (locale-to-lang-code loc)))
          (set-rec [lang-code unicode-set]
            (let [loc (lang-code-to-locale lang-code)]
              {:locale loc
               :name (loc-to-name loc)
               :set unicode-set}))]
    (let [uranges (unicode-ranges)
          lang-to-unicode {"Greek" #{"Greek and Coptic" "Greek Extended"}
                           "Russian" #{"Cyrillic" "Cyrillic Supplementary"}
                           "Hindi" #{"Devanagari"}
                           "Thai" #{"Thai"}
                           "Chinese Traditional" (unicodes-for-regex uranges #"(CJK|Bopomofo)")
                           "Japanese" (unicodes-for-regex uranges #"^(Katakana|Kanbun|Hiragana)")
                           "Korean" (unicodes-for-regex uranges #"^Hangul")
                           "Arabic" (unicodes-for-regex uranges #"^Arabic")}
          pl-alph #{65 97 260 261 66 98 67 99 262 263 68 100 69 101 280 281 70
                    102 71 103 72 104 73 105 74 106 75 107 76 108 321 322 77
                    109 78 110 323 324 79 111 211 243 80 112 82 114 83 115 346
                    347 84 116 85 117 87 119 89 121 90 122 377 378 379 380}
          pl-diff #{260 262 280 321 323 211 346 379 261 263 281 322 324
                    243 347 378 380}
          es-diff #{191 161 241 209}
          de-diff #{223}
          tr-diff #{287 286 305 304 246 214 252 220 351 350 231 199}
          ru-alph (set (range 0x410 0x450))
          uk-alph #{1040 1072 1041 1073 1042 1074 1043 1075 1168 1169 1044
                    1076 1045 1077 1028 1108 1046 1078 1047 1079 1048 1080
                    1030 1110 1031 1111 1049 1081 1050 1082 1051 1083 1084
                    1053 1085 1054 1086 1055 1087 1056 1088 1057 1089 1058
                    1090 1059 1091 1060 1092 1061 1093 1062 1094 1063 1095
                    1064 1096 1065 1097 1068 1100 1070 1102 1071 1103}
          uk-diff (difference ru-alph uk-alph)
          sl-diff #{268 269 352 353 381 382}
          ro-diff #{258 259 194 226 206 238 536 537 538 539}
          umlauts #{196 228 214 246 220 252}
          non-lang-sets [{:name "scandinavian"
                          :set #{198 230 197 229 196 228 216 248 214 246}}
                         {:name "umlauts" :set umlauts}]
          lang-sets [(set-rec "pl" pl-diff)
                     (set-rec "es" es-diff)
                     (set-rec "tr" tr-diff)
                     (set-rec "sl" sl-diff)
                     (set-rec "de" umlauts)]]
      (->> ((or @name-to-locale-fn-inst
                name-to-locale))
           (map (fn [[lang-code loc]]
                  (let [unames (or (get lang-to-unicode lang-code) (list lang-code))
                        uranges (->> unames (map #(get uranges %)) (remove nil?))
                        name (loc-to-name loc)]
                    (if-not (empty? uranges)
                      {name {:locale loc
                             :name name
                             :ranges uranges}}))))
           (remove nil?)
           (concat (map #(hash-map (-> % :locale loc-to-name) %) lang-sets))
           (concat (map #(hash-map (:name %) %) non-lang-sets))
           (apply merge)))))

(defn- unicodes []
  (swap! unicodes-inst #(or % (create-unicodes))))

(defn- create-unicode-to-locale
  "Return a map Unicode names to `java.util.Locale`."
  []
  (->> (unicodes)
       vals
       (map (fn [{:keys [ranges set] :as urec}]
              (if ranges
                (map (fn [rrec]
                       {(:name rrec) {:name (:name rrec)
                                      :range (:range rrec)
                                      :locale (:locale urec)}})
                     ranges)
                (list {(:name urec) urec}))))
       (apply concat)
       (apply merge)))

(defn- unicode-to-locale   []
  (swap! unicode-to-locale-inst #(or % (create-unicode-to-locale))))

(defn- unicode-to-locale-vals []
  (swap! unicode-to-locale-vals-inst #(or % (vals (unicode-to-locale)))))

(defn unicode-for-char
  "Return a sequence of Unicode info maps that are in range for characater
  **c**.  Each map has the following keys:

  * **:name** the name of the Unicode record, which is one of
      * Unicode range name as defined in the `unicode-ranges.csv` resource
      * Particular set of Unicode characters (i.e. *umlaut*)
      * Language name mapped from the `java.util.Locale`.
  * **:range** the numeric Unicode range (if this is missing **:set** isn't)
  * **:set** a hash of Unicode characters (if this is missing **:range** ins't)
  * **:locale** the `java.util.Locale` assigned to the range (if any)

  If best-match? is `true` then return only the best match (i.e. language over
  partial alphabet) per each Unicode match (in range or member of set).
  Another way to say this is that there will not be any overlapping Unicode
  range/set data returned, and thus, results are disjoint."
  ([c]
   (unicode-for-char false))
  ([c best-match?]
   (let [c (int c)]
     (->> (unicode-to-locale-vals)
          (map (fn [{:keys [range set] :as urec}]
                 (if range
                   (let [[start end] range]
                     (if (and (>= c start) (<= c end))
                       urec))
                   (if (contains? set c)
                     urec))))
          (#(if best-match?
              (take 1 (drop-while nil? %))
              (remove nil? %)))))))

(defn unicode-counts
  "Return counts of all characters in **text**.  See [[unicode-for-char]].

  Keys
  ----
  * **:best-match?** see [[unicode-for-char]]; note that counts will differ and
  won't necessarily sum to all combinations of disjoint Unicode ranges/sets"
  [text & {:keys [best-match?] :or {best-match? false}}]
  (->> (map #(unicode-for-char % best-match?) text)
       (apply concat)
       (reduce (fn [stats {:keys [name] :as urec}]
                 (if-not name
                   stats
                   (let [cnt (or (:cnt (get stats name)) 0)]
                     (merge stats {name {:cnt (inc cnt)
                                         :urec urec}}))))
               {})
       vals
       (map (fn [{:keys [cnt urec]}]
              (assoc urec :count cnt)))))

(defn locale-counts
  "Return counts that are a member of a language mapping (locale) of all
  characters in **text**.  See [[unicode-for-char]].

  Keys
  ----
  * **:best-match?** if `true` then return only the best match (i.e. language
  over partial alphabet) per each Unicode range"
  [text & {:keys [best-match?] :or {best-match? false}}]
  (->> (unicode-counts text :best-match? best-match?)
       (map (fn [{:keys [name count]}]
              (let [{:keys [loc]} (get (unicode-to-locale) name)]
                (if loc
                  {(locale-to-lang-code loc) count}))))
       (apply merge)))
