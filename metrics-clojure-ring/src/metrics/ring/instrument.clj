(ns metrics.ring.instrument
  (:use [metrics.counters :only (counter inc! dec!)]
        [metrics.meters :only (meter mark!)]
        [metrics.timers :only (timer time!)])
  (:import [com.codahale.metrics MetricRegistry])
  )


(defn- mark-in! [metric-map k]
  (when-let [metric (metric-map k (metric-map :other))]
    (mark! metric)))

(defn instrument
  "Instrument a ring handler.

  This middleware should be added as late as possible (nearest to the outside of
  the \"chain\") for maximum effect.
  "
  ([handler ^MetricRegistry reg]
   (let [active-requests (counter reg ["ring" "requests" "active"])
         requests (meter reg ["ring" "requests" "rate"])
         responses (meter reg ["ring" "responses" "rate"])
         statuses {2 (meter reg ["ring" "responses" "rate.2xx"])
                   3 (meter reg ["ring" "responses" "rate.3xx"])
                   4 (meter reg ["ring" "responses" "rate.4xx"])
                   5 (meter reg ["ring" "responses" "rate.5xx"])}
         times {:get     (timer reg ["ring" "handling-time" "GET"])
                :put     (timer reg ["ring" "handling-time" "PUT"])
                :post    (timer reg ["ring" "handling-time" "POST"])
                :head    (timer reg ["ring" "handling-time" "HEAD"])
                :delete  (timer reg ["ring" "handling-time" "DELETE"])
                :options (timer reg ["ring" "handling-time" "OPTIONS"])
                :trace   (timer reg ["ring" "handling-time" "TRACE"])
                :connect (timer reg ["ring" "handling-time" "CONNECT"])
                :other   (timer reg ["ring" "handling-time" "OTHER"])}
         request-methods {:get     (meter reg ["ring" "requests" "rate.GET"])
                          :put     (meter reg ["ring" "requests" "rate.PUT"])
                          :post    (meter reg ["ring" "requests" "rate.POST"])
                          :head    (meter reg ["ring" "requests" "rate.HEAD"])
                          :delete  (meter reg ["ring" "requests" "rate.DELETE"])
                          :options (meter reg ["ring" "requests" "rate.OPTIONS"])
                          :trace   (meter reg ["ring" "requests" "rate.TRACE"])
                          :connect (meter reg ["ring" "requests" "rate.CONNECT"])
                          :other   (meter reg ["ring" "requests" "rate.OTHER"])}]
     (fn [request]
       (inc! active-requests)
       (try
         (let [request-method (:request-method request)]
           (mark! requests)
           (mark-in! request-methods request-method)
           (let [resp (time! (times request-method (times :other))
                             (handler request))
                 status-code (or (:status resp) 404)]
             (mark! responses)
             (mark-in! statuses (int (/ status-code 100)))
             resp))
         (finally (dec! active-requests)))))))
