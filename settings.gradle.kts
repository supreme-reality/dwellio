rootProject.name = "dwellio"

include("apps:api")
include("apps:billing-worker")
include("apps:invoice-worker")

project(":apps:api").projectDir = file("apps/api")
project(":apps:billing-worker").projectDir = file("apps/billing-worker")
project(":apps:invoice-worker").projectDir = file("apps/invoice-worker")
