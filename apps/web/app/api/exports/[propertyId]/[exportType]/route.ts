import { auth0 } from "@/lib/auth0";
import { downloadExportCsv } from "@/lib/dwellio-api/analytics";
import { NextRequest, NextResponse } from "next/server";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ propertyId: string; exportType: string }> },
) {
  const { propertyId, exportType } = await context.params;
  if (!["tenants", "expenses", "invoices"].includes(exportType)) {
    return NextResponse.json({ message: "Invalid export type" }, { status: 400 });
  }

  const session = await auth0.getSession();
  if (!session) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }
  const { token } = await auth0.getAccessToken();
  if (!token) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  try {
    const csv = await downloadExportCsv(
      token,
      propertyId,
      exportType as "tenants" | "expenses" | "invoices",
    );
    return new NextResponse(csv, {
      headers: {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": `attachment; filename="${exportType}.csv"`,
      },
    });
  } catch (error) {
    return NextResponse.json(
      { message: error instanceof Error ? error.message : "Export failed" },
      { status: 500 },
    );
  }
}
