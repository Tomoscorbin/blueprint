(ns blueprint.tui.terminal
  "Terminal detection and basic styling helpers for the TUI.

  This namespace owns:
  - The shared JLine Terminal instance
  - Capability checks (ANSI, arrow-menu support)
  - Simple styling and line-rewriting helpers."
  (:import
   [org.jline.terminal TerminalBuilder Terminal]))

;; ----- Environment / terminal detection ------------------------------------

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

(defn- build-terminal
  "Create the shared JLine Terminal.

  - Always try to use the *system* terminal so JLine can toggle raw mode.
  - On Windows (incl. MSYS/Git Bash), prefer the JNI provider if present.
  - If no system terminal can be created (CI, redirected IO, etc.), fall back
    to a dumb terminal instead of throwing."
  ^Terminal
  []
  (let [builder (doto (TerminalBuilder/builder)
                  ;; Always wrap the real process terminal
                  (.system true)
                  ;; If a system terminal can't be created, use a dumb one
                  ;; instead of failing with \"Unable to create a system terminal\".
                  (.dumb true))]
    (when (windows?)
      (try
        ;; For Windows / Cygwin / MSYS JLine’s own docs recommend the native
        ;; (JNI / FFM) providers for proper behaviour. 
        (.provider builder "jni")
        (catch Throwable _
          ;; If the JNI provider is not on the classpath, just fall back to the
          ;; default provider selection.
          )))
    (.build builder)))

(def ^:private terminal*
  (delay (build-terminal)))

(defn terminal
  "Return the shared JLine Terminal instance."
  ^Terminal
  []
  @terminal*)

(defn- dumb-terminal?
  "True if JLine thinks this is a 'dumb' terminal (no real capabilities)."
  []
  (let [^Terminal t (terminal)
        term-type (.getType t)]
    (#{"dumb" "dumb-color"} term-type)))

(defn ansi-capable?
  "Return true if this environment is one where ANSI escape sequences are
  generally safe and likely to be interpreted correctly.

  Heuristic:
  - Exclude 'dumb' terminals (redirected IO / CI).
  - Non-Windows: assume ANSI capable.
  - Windows + MSYS (Git Bash): assume ANSI capable.
  - Plain Windows consoles (PowerShell/cmd without MSYS): treat as non-ANSI."
  []
  (and (not (dumb-terminal?))
       (or (not (windows?))
           (msys?))))

(defn arrow-menu-supported?
  "Return true if we are happy to use the arrow-key TUI.

  Rules:
  - Only on non-dumb terminals.
  - On non-Windows, enable the arrow menu (Linux/macOS).
  - On Windows + MSYS (Git Bash), enable the arrow menu.
  - On plain Windows consoles (PowerShell/cmd without MSYS), disable it."
  []
  (and (not (dumb-terminal?))
       (or (not (windows?))
           (msys?))))

;; ----- Styling / question helpers ------------------------------------------

(def ^:private esc "\u001b[")

(defn style-answer
  "Style an answer string for display in a prompt.

  On ANSI-capable terminals, this is bold green; elsewhere it is returned
  unchanged."
  [s]
  (if (ansi-capable?)
    (str esc "1;32m" s esc "0m")
    s))

(defn rewrite-prev-line!
  "Rewrite the previous terminal line with string `s`.

  On ANSI-capable terminals:
  - Move the cursor up one line,
  - Clear it,
  - Print `s`, then move to a new line.

  On non-ANSI terminals, this is a no-op."
  [s]
  (when (ansi-capable?)
    (print (str esc "1A" "\r" esc "2K" s "\n"))
    (flush))
  nil)
