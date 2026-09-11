import { auth0 } from "@/lib/auth0";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { resolveRent } from "@/lib/dwellio-api/rent";
import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  const propertyId = request.nextUrl.searchParams.get("propertyId");
  const bedId = request.nextUrl.searchParams.get("bedId");
  const date = request.nextUrl.searchParams.get("date");
  if (!propertyId || !bedId || !date) {
    return NextResponse.json(
      { message: "propertyId, bedId, and date are required" },
      { status: 400 },
    );
  }

  try {
    const session = await auth0.getSession();
    if (!session) {
      return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
    }
    const { token } = await auth0.getAccessToken();
    if (!token) {
      return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
    }
    const result = await resolveRent(token, propertyId, bedId, date);
    return NextResponse.json(result);
  } catch (error) {
    if (error instanceof DwellioApiError) {
      return NextResponse.json(
        {
          message: error.message,
          code: error.code,
          requestId: error.requestId,
        },
        { status: error.status },
      );
    }
    return NextResponse.json(
      { message: error instanceof Error ? error.message : "Resolve failed" },
      { status: 500 },
    );
  }
}
