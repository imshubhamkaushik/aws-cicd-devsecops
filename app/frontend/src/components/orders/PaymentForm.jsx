import React, { useState } from "react";
import PropTypes from "prop-types";
import { payOrder } from "../../api";
import { formatPrice } from "../../lib/format";
import { Button } from "../ui/button";
import { Input, Select, Field } from "../ui/field";

function PaymentForm({ order, onPaid, onError }) {
  const [method, setMethod] = useState("CARD");
  const [cardLast4, setCardLast4] = useState("");
  const [upiId, setUpiId] = useState("");
  const [paying, setPaying] = useState(false);
  // Stable for the lifetime of this payment form. A retry after a transport
  // timeout therefore repeats the same logical payment instead of charging
  // twice. We intentionally reset it whenever the payment inputs change.
  const [idempotencyKey, setIdempotencyKey] = useState(() =>
    crypto.randomUUID(),
  );

  let paymentButtonText = `Pay ${formatPrice(order.totalAmount)}`;

  if (method === "COD") {
    paymentButtonText = `Confirm ${formatPrice(order.totalAmount)} on delivery`;
  }

  if (paying) {
    paymentButtonText = "Processing…";
  }

  const handlePay = async (event) => {
    event.preventDefault();
    setPaying(true);

    let cardValue;
    let upiValue;

    if (method === "CARD") {
      cardValue = cardLast4 || "4242";
    } else if (method === "UPI") {
      upiValue = upiId || "buyer@upi";
    }

    try {
      const result = await payOrder(
        order.id,
        method,
        cardValue,
        upiValue,
        idempotencyKey,
      );

      onPaid(result);
    } catch (error) {
      onError(error.response?.data?.message || "Payment failed.");
    } finally {
      setPaying(false);
    }
  };

  return (
    <form
      className="mt-4 rounded-control border border-line bg-surface-2 p-3 sm:p-4"
      onSubmit={handlePay}
    >
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Payment method" className="w-full sm:w-52">
          {(id) => (
            <Select
              id={id}
              value={method}
              onChange={(event) => {
                setMethod(event.target.value);
                setIdempotencyKey(crypto.randomUUID());
              }}
              disabled={paying}
            >
              <option value="CARD">Card</option>
              <option value="UPI">UPI</option>
              <option value="COD">Cash on Delivery</option>
            </Select>
          )}
        </Field>

        {method === "CARD" && (
          <Field label="Card last 4 digits" className="w-full sm:w-40">
            {(id) => (
              <Input
                id={id}
                inputMode="numeric"
                placeholder="4242"
                maxLength={4}
                value={cardLast4}
                onChange={(event) => {
                  setCardLast4(event.target.value.replace(/\D/g, ""));
                  setIdempotencyKey(crypto.randomUUID());
                }}
                disabled={paying}
              />
            )}
          </Field>
        )}

        {method === "UPI" && (
          <Field label="UPI ID" className="w-full sm:w-52">
            {(id) => (
              <Input
                id={id}
                placeholder="you@upi"
                value={upiId}
                onChange={(event) => {
                  setUpiId(event.target.value);
                  setIdempotencyKey(crypto.randomUUID());
                }}
                disabled={paying}
              />
            )}
          </Field>
        )}

        <Button type="submit" disabled={paying} className="w-full sm:w-auto">
          {paymentButtonText}
        </Button>
      </div>

      <p className="mt-3 text-xs text-ink-2">
        {method === "CARD" &&
          'Mock payment — any card works, "0000" simulates a decline.'}

        {method === "UPI" &&
          'Mock payment — any VPA works, one starting with "fail@" simulates a decline.'}

        {method === "COD" &&
          "Pay in cash when your order arrives — no payment is captured now."}
      </p>
    </form>
  );
}

PaymentForm.propTypes = {
  order: PropTypes.shape({
    id: PropTypes.number,
    totalAmount: PropTypes.number,
  }).isRequired,
  onPaid: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};
