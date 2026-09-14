rootProject.name = "dwellio"

include("apps:api")
include("apps:worker")

project(":apps:api").projectDir = file("apps/api")
project(":apps:worker").projectDir = file("apps/worker")
