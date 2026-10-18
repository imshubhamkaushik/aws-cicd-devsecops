import React from "react";
import PropTypes from "prop-types";
import { MapPin, Plus } from "lucide-react";
import { Button } from "../ui/button";
import { Input, Field } from "../ui/field";
import { Badge } from "../ui/badge";
import { Panel, Skeleton } from "../ui/surface";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "../ui/dialog";
import Section from "./Section";

const idType = PropTypes.oneOfType([PropTypes.number, PropTypes.string]);

export default function AddressesSection({
  addresses,
  loading,
  form,
  setForm,
  editingId,
  formOpen,
  submitting,
  busyId,
  submitLabel,
  error,
  onAdd,
  onEdit,
  onCancel,
  onSubmit,
  onSetDefault,
  onDelete,
}) {
  const bind = (key) => ({
    value: form[key],
    onChange: (e) => setForm((current) => ({ ...current, [key]: e.target.value })),
  });

  return (
    <Section
      id="addresses"
      title="Saved addresses"
      description="Delivery addresses you can pick at checkout."
    >
      <Panel className="divide-y divide-line">
        {loading && (
          <div className="space-y-2 p-4 sm:p-5">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-4 w-64" />
          </div>
        )}

        {!loading && addresses.length === 0 && (
          <div className="flex items-center gap-3 p-4 text-sm text-ink-2 sm:p-5">
            <MapPin className="size-4 shrink-0" aria-hidden="true" />
            <p>No saved addresses yet.</p>
          </div>
        )}

        {!loading &&
          addresses.map((address) => (
            <div
              key={address.id}
              className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between sm:p-5"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 font-medium">
                  {address.label}
                  {address.default && <Badge tone="success">Default</Badge>}
                </div>
                <p className="mt-0.5 text-sm text-ink-2">
                  {address.line1}
                  {address.line2 ? `, ${address.line2}` : ""}
                  <br />
                  {address.city}, {address.state} {address.pincode} · {address.phone}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {!address.default && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onSetDefault(address.id)}
                    disabled={busyId === address.id}
                  >
                    {busyId === address.id ? "Saving…" : "Set default"}
                  </Button>
                )}
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => onEdit(address)}
                  disabled={busyId === address.id}
                >
                  Edit
                </Button>
                <Button
                  variant="danger-outline"
                  size="sm"
                  onClick={() => onDelete(address.id)}
                  disabled={busyId === address.id}
                >
                  Delete
                </Button>
              </div>
            </div>
          ))}

        <div className="p-4 sm:p-5">
          <Button variant="secondary" onClick={onAdd}>
            <Plus aria-hidden="true" />
            Add new address
          </Button>
        </div>
      </Panel>

      <Dialog open={formOpen} onOpenChange={(open) => !open && !submitting && onCancel()}>
        <DialogContent>
          <DialogTitle>{editingId ? "Edit address" : "Add address"}</DialogTitle>
          <DialogDescription>Where should we deliver your orders?</DialogDescription>
          {error && (
            <p role="alert" className="mt-4 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger">
              {error}
            </p>
          )}
          <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <Field label="Label" className="sm:col-span-2">
              {(id) => <Input id={id} placeholder="Home" required {...bind("label")} />}
            </Field>
            <Field label="Address line 1" className="sm:col-span-2">
              {(id) => <Input id={id} required {...bind("line1")} />}
            </Field>
            <Field label="Address line 2" className="sm:col-span-2">
              {(id) => <Input id={id} {...bind("line2")} />}
            </Field>
            <Field label="City">{(id) => <Input id={id} required {...bind("city")} />}</Field>
            <Field label="State">{(id) => <Input id={id} required {...bind("state")} />}</Field>
            <Field label="Pincode">
              {(id) => <Input id={id} required inputMode="numeric" {...bind("pincode")} />}
            </Field>
            <Field label="Phone">
              {(id) => <Input id={id} required type="tel" {...bind("phone")} />}
            </Field>
            {!editingId && (
              <label className="flex min-h-9 items-center gap-2 text-sm font-medium sm:col-span-2">
                <input
                  type="checkbox"
                  className="size-4 accent-brand"
                  checked={form.makeDefault}
                  onChange={(e) =>
                    setForm((current) => ({ ...current, makeDefault: e.target.checked }))
                  }
                />
                Make default
              </label>
            )}
            <div className="flex flex-wrap justify-end gap-2 sm:col-span-2">
              <Button variant="ghost" onClick={onCancel} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitLabel}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </Section>
  );
}

AddressesSection.propTypes = {
  addresses: PropTypes.arrayOf(
    PropTypes.shape({
      id: idType.isRequired,
      label: PropTypes.string,
      line1: PropTypes.string,
      line2: PropTypes.string,
      city: PropTypes.string,
      state: PropTypes.string,
      pincode: PropTypes.string,
      phone: PropTypes.string,
      default: PropTypes.bool,
    }),
  ).isRequired,
  loading: PropTypes.bool.isRequired,
  form: PropTypes.shape({ makeDefault: PropTypes.bool }).isRequired,
  setForm: PropTypes.func.isRequired,
  editingId: idType,
  formOpen: PropTypes.bool.isRequired,
  submitting: PropTypes.bool.isRequired,
  busyId: idType,
  submitLabel: PropTypes.string.isRequired,
  error: PropTypes.string,
  onAdd: PropTypes.func.isRequired,
  onEdit: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  onSubmit: PropTypes.func.isRequired,
  onSetDefault: PropTypes.func.isRequired,
  onDelete: PropTypes.func.isRequired,
};
