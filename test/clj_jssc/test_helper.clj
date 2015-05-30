(ns clj-jssc.test-helper
  (:require [clj-jssc.test-helper :refer :all]
            [clojure.test :refer :all])
  (:use [clojure.java.shell :only [sh]]))

;; Since we use socat to test this application, check if it exist first
(when-not (zero? (:exit (sh "type" "socat")))
  (throw (Exception. (str "Please install socat first."))))

(defn- create-tmpfile
  "Create tmpfile for testing with socat. This function will return tmp file
  absolute path."
  []
  (let [tmp (java.io.File/createTempFile "tty" ".serial")]
    ;; mark  the temporary file to be delete automatically when JVM exits.
    ;;(.deleteOnExit tmp)
    ;; return absolute path
    (.getAbsolutePath tmp)))

(def pid-list (atom []))

(defn mock-serial
  []
  (let [tty1 (create-tmpfile) tty2 (create-tmpfile)
        socat (format "socat PTY,raw,link=%s PTY,raw,link=%s" tty1 tty2)
        cmd (format "nohup %s < /dev/null &> /dev/null & echo $! " socat)
        exec (sh "bash" "-c" cmd)
        pid (clojure.string/trim (:out exec))]
    (if (zero? (:exit exec))
      ;; register pid info to let JVM know which process need to kill
      ;; before exist
      (swap! pid-list conj pid)
      ;; error case
      (throw (Exception. (str "socat execute failed, error: " pid))))
    ;; return
    (merge exec {:tty1 tty1 :tty2 tty2 :pid pid})))


;; Before leave JVM, we need to close all socat process we create
(.addShutdownHook
 (Runtime/getRuntime)
 (Thread.
  (fn []
    ;; FIXME: why this will run twice ?
    (doseq [pid @pid-list]
;;      (sh "kill" "-SIGTERM" pid)
;;      (println (format "kill socat with PID %s\n" pid))
      ))))