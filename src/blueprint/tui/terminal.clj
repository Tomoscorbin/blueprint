(ns blueprint.tui.terminal
  "Terminal detection and basic styling helpers for the TUI.

  This namespace owns:
  - The shared JLine Terminal instance
  - Capability checks (ANSI, arrow-menu support)
  - Simple styling and line-rewriting helpers."
  (:import
   [org.jline.terminal TerminalBuilder Terminal]))

(def esc
  "ANSI escape prefix used for constructing control sequences."
  "\u001b[")

(defn green-bold
  "Wrap `s` in a bold green SGR sequence."
  [s]
  (str esc "1;32m" s esc "0m"))

(defn print-ansi!
  "Print a raw ANSI sequence (or plain text) and flush immediately."
  [& parts]
  (print (apply str parts))
  (flush))

;; ----- Environment / OS detection -----------------------------------------

(defn windows?
  "Return true if we are running on a Windows OS."
  []
  (-> (System/getProperty "os.name")
      (.toLowerCase)
      (.contains "win")))

(defn msys?
  "Return true if we are running under an MSYS / Git Bash environment on Windows."
  []
  (some? (System/getenv "MSYSTEM")))

(defn- windows-mintty?
  "True when running on Windows under an MSYS/Git Bash environment (mintty)."
  []
  (and (windows?) (msys?)))

;; ----- Terminal construction -----------------------------------------------

(defn- build-terminal
  "Create the shared JLine Terminal.

  Behaviour:
  - Always try to use the *system* terminal so JLine can toggle raw mode.
  - On Windows (incl. MSYS/Git Bash), prefer the JNI provider if present.
  - If no system terminal can be created (CI, redirected IO, etc.), fall back
    to a dumb terminal instead of throwing."
  ^Terminal
  []
  (let [builder (doto (TerminalBuilder/builder)
                  (.system true)
                  (.dumb true))]
    (when (windows?)
      (try
        (.provider builder "jni")
        (catch Throwable _)))
    (.build builder)))

(def ^:private terminal*
  (delay (build-terminal)))

(defn terminal
  "Return the shared JLine Terminal instance."
  ^Terminal
  []
  @terminal*)

(defn- terminal-type
  "Return the JLine terminal type string (e.g. \"xterm-256color\", \"dumb\")."
  []
  (let [^Terminal t (terminal)]
    (.getType t)))

(defn- dumb-terminal?
  "True if JLine thinks this is a 'dumb' terminal (no real capabilities)."
  []
  (#{"dumb" "dumb-color"} (terminal-type)))

;; ----- Capability checks ---------------------------------------------------

(defn ansi-capable?
  "Return true if this environment is one where ANSI escape sequences are
  generally safe and likely to be interpreted correctly."
  []
  (and (not (dumb-terminal?))
       (or (not (windows?))
           (windows-mintty?))))

(defn arrow-menu-supported?
  "Return true if we are happy to use the arrow-key TUI."
  []
  (and (not (dumb-terminal?))
       (or (not (windows?))
           (windows-mintty?))))

;; ----- Low-level cursor/line helpers --------------------------------------

(defn save-cursor!
  "Save the current cursor position using ANSI, if supported."
  []
  (when (ansi-capable?)
    (print (str esc "s"))
    (flush)))

(defn restore-cursor!
  "Restore the cursor to the last saved position using ANSI, if supported."
  []
  (when (ansi-capable?)
    (print (str esc "u"))
    (flush)))

(defn clear-line!
  "Clear the entire current line using ANSI, if supported."
  []
  (when (ansi-capable?)
    (print (str esc "2K"))
    (flush)))

;; ----- Styling / question helpers ------------------------------------------

(defn style-answer
  "Style an answer string for display in a prompt.

  On ANSI-capable terminals, this is bold green; elsewhere it is returned
  unchanged."
  [s]
  (if (ansi-capable?)
    (green-bold s)
    s))

(defn rewrite-prev-line!
  "Rewrite the previous terminal line with string `s`."
  [s]
  (when (ansi-capable?)
    ;; Move cursor up one line, carriage return, clear it, then print s + newline.
    (print-ansi! esc "1A" "\r")
    (clear-line!)
    (print-ansi! s "\n"))
  nil)
