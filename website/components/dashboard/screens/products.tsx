"use client";

import { Archive, ArchiveRestore, Boxes, Package, PackagePlus, Pencil } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader, DescriptionList } from "@/components/ui/card";
import { Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/endpoints";
import type { Category, ProductSummary } from "@/lib/api/types";
import { date, money, percent, qty } from "@/lib/format";
import { useApi, useDashboard, useDebounced } from "../context";
import { PageHeader, SearchBox } from "../parts";
import { StockOperationDialog } from "../stock-dialog";
import { MovementList } from "./overview";
import { ImagesCard, TrackingCard, VariantsCard } from "./product-extras";

export function flattenCategories(tree: Category[], out: Category[] = []): Category[] {
  for (const c of tree) {
    out.push(c);
    flattenCategories(c.children ?? [], out);
  }
  return out;
}

export function ProductsScreen() {
  const { client, href, can, currency } = useDashboard();
  const params = useSearchParams();
  const [q, setQ] = useState("");
  const dq = useDebounced(q);
  const [status, setStatus] = useState(params.get("status") ?? "");
  const [categoryId, setCategoryId] = useState("");
  const [sort, setSort] = useState("name");
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [dq, status, categoryId, sort, archived]);

  const cats = useApi(() => api.catalog.categories(client), [client]);
  const list = useApi(() => api.products.list(client, { q: dq, status, categoryId, sort, archived, page, size: 20 }), [client, dq, status, categoryId, sort, archived, page]);
  const flat = useMemo(() => flattenCategories(cats.data ?? []), [cats.data]);

  return (
    <>
      <PageHeader
        title="Products"
        description="Your product catalogue with live stock levels."
        actions={can("PRODUCT_CREATE") && <ButtonLink href={href("/products/new")} icon={<PackagePlus className="size-4" aria-hidden />}>Add product</ButtonLink>}
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-line p-4 lg:flex-row lg:items-center">
          <SearchBox value={q} onChange={setQ} placeholder="Search name, brand, SKU or barcode" label="Search products" />
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:ml-auto lg:flex">
            <label className="sr-only" htmlFor="f-status">Stock status</label>
            <Select id="f-status" value={status} onChange={(e) => setStatus(e.target.value)} className="lg:w-40">
              <option value="">All stock levels</option>
              <option value="IN_STOCK">In stock</option>
              <option value="LOW_STOCK">Low stock</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
            </Select>
            <label className="sr-only" htmlFor="f-cat">Category</label>
            <Select id="f-cat" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className="lg:w-44">
              <option value="">All categories</option>
              {flat.map((c) => (
                <option key={c.id} value={c.id}>
                  {"  ".repeat(c.depth)}
                  {c.name}
                </option>
              ))}
            </Select>
            <label className="sr-only" htmlFor="f-sort">Sort</label>
            <Select id="f-sort" value={sort} onChange={(e) => setSort(e.target.value)} className="lg:w-40">
              <option value="name">Name A–Z</option>
              <option value="updated">Recently updated</option>
              <option value="created">Recently added</option>
            </Select>
            <label className="sr-only" htmlFor="f-arch">Archive state</label>
            <Select id="f-arch" value={archived ? "1" : ""} onChange={(e) => setArchived(e.target.value === "1")} className="lg:w-36">
              <option value="">Active</option>
              <option value="1">Archived</option>
            </Select>
          </div>
        </div>
        {list.error ? (
          <ErrorState error={list.error} onRetry={list.reload} />
        ) : !list.data ? (
          <LoadingBlock rows={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={Package} title={dq || status || categoryId ? "No products match these filters" : "No products yet"} description={dq || status || categoryId ? "Try clearing a filter." : "Add your first product to start tracking stock."} />
        ) : (
          <div aria-busy={list.loading}>
            <DataTable<ProductSummary>
              caption="Products"
              rows={list.data.content}
              rowKey={(r) => r.id}
              rowHref={(r) => href(`/products/${r.id}`)}
              columns={[
                { header: "Product", cell: (r) => r.name },
                { header: "SKU", cell: (r) => r.sku ?? "—", hideOnMobile: true },
                { header: "Category", cell: (r) => r.categoryName ?? "Uncategorized", hideOnMobile: true },
                { header: "Available", align: "right", cell: (r) => `${qty(r.stock.available)} ${r.unitCode}` },
                { header: "Selling price", align: "right", cell: (r) => money(r.salePrice, currency) },
                { header: "Status", cell: (r) => <StatusChip status={r.archived ? "ARCHIVED" : r.status} /> },
              ]}
            />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
          </div>
        )}
      </Card>
    </>
  );
}

export function ProductDetailScreen({ id }: { id: number }) {
  const { client, href, can, currency } = useDashboard();
  const toast = useToast();
  const product = useApi(() => api.products.get(client, id), [client, id]);
  const movements = useApi(() => api.stock.movements(client, { productId: id, size: 10 }), [client, id]);
  const [stockOpen, setStockOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const p = product.data;

  if (product.error) return <Card><ErrorState error={product.error} onRetry={product.reload} /></Card>;
  if (!p) return <Card><LoadingBlock rows={8} /></Card>;

  const defaultVariant = p.variants.find((v) => v.isDefault) ?? p.variants[0];
  const toggleArchive = async () => {
    setBusy(true);
    try {
      const next = p.archived ? await api.products.restore(client, p.id) : await api.products.archive(client, p.id);
      product.setData(next);
      toast(next.archived ? "Product archived" : "Product restored");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        back={{ href: href("/products"), label: "Products" }}
        title={p.name}
        description={[p.brand, p.categoryPath ?? "Uncategorized", defaultVariant?.sku].filter(Boolean).join(" · ")}
        actions={
          <>
            {can("STOCK_ADD", "STOCK_REDUCE", "STOCK_ADJUST", "STOCK_TRANSFER") && !p.archived && (
              <Button icon={<Boxes className="size-4" aria-hidden />} onClick={() => setStockOpen(true)}>
                Update stock
              </Button>
            )}
            {can("PRODUCT_EDIT") && <ButtonLink href={href(`/products/${p.id}/edit`)} variant="secondary" icon={<Pencil className="size-4" aria-hidden />}>Edit</ButtonLink>}
            {can("PRODUCT_ARCHIVE") && (
              <Button variant="secondary" loading={busy} onClick={toggleArchive} icon={p.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}>
                {p.archived ? "Restore" : "Archive"}
              </Button>
            )}
          </>
        }
      />
      {p.archived && <Notice tone="warning" className="mb-4">This product is archived. It is hidden from lists and can&apos;t be sold until restored.</Notice>}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              ["Available", p.stock.available],
              ["On hand", p.stock.onHand],
              ["Reserved", p.stock.reserved],
              ["Damaged", p.stock.damaged],
            ].map(([label, v]) => (
              <div key={label as string} className="rounded-xl border border-line bg-surface p-4">
                <p className="text-xs font-medium text-muted">{label}</p>
                <p className="mt-1 text-xl font-semibold tabular">
                  {qty(v as number, p.baseUnit.decimals)} <span className="text-sm font-normal text-muted">{p.baseUnit.code}</span>
                </p>
              </div>
            ))}
          </div>

          <Card>
            <CardHeader title="Stock by warehouse" action={<StatusChip status={p.status} />} />
            {p.balances.length === 0 ? (
              <EmptyState title="No stock recorded yet" />
            ) : (
              <DataTable
                caption="Stock by warehouse"
                rows={p.balances}
                rowKey={(b) => `${b.warehouseId}-${b.variantId}-${b.batchId ?? 0}`}
                columns={[
                  { header: "Warehouse", cell: (b) => b.warehouseName },
                  ...(p.variants.length > 1 ? [{ header: "Variant", cell: (b: (typeof p.balances)[number]) => b.variantName }] : []),
                  { header: "On hand", align: "right", cell: (b) => qty(b.onHand) },
                  { header: "Reserved", align: "right", cell: (b) => qty(b.reserved) },
                  { header: "Damaged", align: "right", cell: (b) => qty(b.damaged) },
                  { header: "Available", align: "right", cell: (b) => <strong>{qty(b.available)}</strong> },
                ]}
              />
            )}
          </Card>

          <VariantsCard product={p} onChange={(next) => product.setData(next)} />
          {(p.trackBatches || p.trackSerials) && <TrackingCard product={p} />}

          <Card>
            <CardHeader title="Recent stock history" />
            {movements.error ? <ErrorState error={movements.error} onRetry={movements.reload} /> : !movements.data ? <LoadingBlock /> : <MovementList movements={movements.data.content} />}
          </Card>
        </div>

        <div className="space-y-6">
          <ImagesCard product={p} onChange={(next) => product.setData(next)} />
          <Card>
            <CardHeader title="Pricing" description="Prices exclude tax." />
            <CardBody>
              <DescriptionList
                className="sm:grid-cols-1"
                items={[
                  { label: "Selling price", value: money(p.salePrice, currency) },
                  ...(p.purchasePrice !== undefined || p.averageCost !== undefined
                    ? [
                        { label: "Purchase price (from supplier)", value: money(p.purchasePrice, currency) },
                        { label: "Average cost", value: p.averageCost !== undefined ? money(p.averageCost, currency) : "No purchases recorded yet" },
                      ]
                    : []),
                  { label: "Tax rate", value: p.taxRate !== undefined ? percent(p.taxRate, 0) : "—" },
                  ...(p.pricing
                    ? [
                        { label: "Profit per unit", value: `${money(p.pricing.profitPerUnit, currency)} (${percent(p.pricing.marginPercent)})` },
                        { label: "Stock value at cost", value: money(p.pricing.stockValueAtCost, currency) },
                        { label: "Potential profit on available stock", value: money(p.pricing.potentialProfit, currency) },
                      ]
                    : []),
                ]}
              />
              {p.pricing?.costSource === "PURCHASE_PRICE" && <p className="mt-3 text-xs text-muted">Cost uses the purchase price until purchases are recorded.</p>}
              {p.purchasePrice === undefined && p.averageCost === undefined && <p className="mt-3 text-xs text-muted">Costs are visible to members with analytics, purchase or product-edit permission.</p>}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Details" />
            <CardBody>
              <DescriptionList
                className="sm:grid-cols-1"
                items={[
                  { label: "Unit", value: `${p.baseUnit.name} (${p.baseUnit.code})` },
                  { label: "Minimum stock", value: `${qty(p.effectiveMinStock)} ${p.baseUnit.code}${p.minStock === undefined ? " (business default)" : ""}` },
                  { label: "Barcode", value: defaultVariant?.barcode ?? "—" },
                  { label: "Supplier", value: p.supplierName ?? "—" },
                  { label: "Added", value: date(p.createdAt) },
                  { label: "Last updated", value: date(p.updatedAt) },
                  ...Object.entries(p.attributes ?? {}).map(([k, v]) => ({ label: k, value: String(v) })),
                ]}
              />
              {p.description && <p className="mt-4 whitespace-pre-line text-sm text-ink-2">{p.description}</p>}
            </CardBody>
          </Card>
        </div>
      </div>

      {defaultVariant && (
        <StockOperationDialog
          open={stockOpen}
          onClose={() => setStockOpen(false)}
          onDone={() => {
            product.reload();
            movements.reload();
          }}
          initial={p.variants.length === 1 ? { product: p, variant: defaultVariant } : undefined}
        />
      )}
    </>
  );
}
