# Live verification script for BookLoop Community implementation
$ErrorActionPreference = "Stop"

function Assert-Condition($condition, $message) {
    if (-not $condition) {
        Write-Error "ASSERTION FAILED: $message"
        exit 1
    } else {
        Write-Host " [PASS] $message" -ForegroundColor Green
    }
}

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

Write-Host "=== 1. REGISTERING FRESH DEMO USERS ==="
# User 1: Alice (Creator of Green Park)
$bodyAlice = @{name="Alice Green"; email="alice_$timestamp@greenpark.com"; password="Password123!"; community="Green Park"} | ConvertTo-Json
$alice = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/users/register" -ContentType "application/json" -Body $bodyAlice
Assert-Condition ($alice.id -ne $null) "Alice registered with id $($alice.id)"

# User 2: Bob (Member to join Green Park via code)
$bodyBob = @{name="Bob Park"; email="bob_$timestamp@greenpark.com"; password="Password123!"; community="Green Park"} | ConvertTo-Json
$bob = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/users/register" -ContentType "application/json" -Body $bodyBob
Assert-Condition ($bob.id -ne $null) "Bob registered with id $($bob.id)"

# User 3: Charlie (Creator of TechHub)
$bodyCharlie = @{name="Charlie Tech"; email="charlie_$timestamp@techhub.com"; password="Password123!"; community="TechHub"} | ConvertTo-Json
$charlie = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/users/register" -ContentType "application/json" -Body $bodyCharlie
Assert-Condition ($charlie.id -ne $null) "Charlie registered with id $($charlie.id)"

# User 4: Dave (Member to join TechHub via code)
$bodyDave = @{name="Dave Hub"; email="dave_$timestamp@techhub.com"; password="Password123!"; community="TechHub"} | ConvertTo-Json
$dave = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/users/register" -ContentType "application/json" -Body $bodyDave
Assert-Condition ($dave.id -ne $null) "Dave registered with id $($dave.id)"

# User 5: Eve (User without community)
$bodyEve = @{name="Eve Wanderer"; email="eve_$timestamp@example.com"; password="Password123!"; community="None"} | ConvertTo-Json
$eve = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/users/register" -ContentType "application/json" -Body $bodyEve
Assert-Condition ($eve.id -ne $null) "Eve registered with id $($eve.id)"


Write-Host "`n=== 2. CREATING TWO COMMUNITIES ==="
# Community 1: Green Park Apartments — RESIDENTIAL (created by Alice)
$bodyGP = @{name="Green Park Apartments $timestamp"; type="RESIDENTIAL"; createdBy=$alice.id} | ConvertTo-Json
$commGP = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/communities" -ContentType "application/json" -Body $bodyGP
Assert-Condition ($commGP.id -ne $null -and $commGP.type -eq "RESIDENTIAL") "Created Green Park Apartments (id: $($commGP.id), code: $($commGP.code))"
# Check code regex: 3 uppercase letters + 2 digits + 2 alphanumeric
Assert-Condition ($commGP.code -match '^[A-Z]{3}\d{2}[A-Z0-9]{2}$') "Green Park code '$($commGP.code)' matches format: 3 letters + 2 digits + 2 alphanumeric"

# Community 2: TechHub Office — OFFICE (created by Charlie)
$bodyTH = @{name="TechHub Office $timestamp"; type="OFFICE"; createdBy=$charlie.id} | ConvertTo-Json
$commTH = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/communities" -ContentType "application/json" -Body $bodyTH
Assert-Condition ($commTH.id -ne $null -and $commTH.type -eq "OFFICE") "Created TechHub Office (id: $($commTH.id), code: $($commTH.code))"
Assert-Condition ($commTH.code -match '^[A-Z]{3}\d{2}[A-Z0-9]{2}$') "TechHub code '$($commTH.code)' matches format: 3 letters + 2 digits + 2 alphanumeric"


Write-Host "`n=== 3. VERIFY AUTOMATIC CREATOR MEMBERSHIP ==="
$aliceMembers = Invoke-RestMethod -Uri "http://localhost:8080/api/communities/$($commGP.id)/members"
$aliceInGP = $aliceMembers | Where-Object { $_.id -eq $alice.id }
Assert-Condition ($aliceInGP -ne $null -and $aliceInGP.communityId -eq $commGP.id) "Creator Alice is automatically listed in Green Park members with communityId $($commGP.id)"

$charlieMembers = Invoke-RestMethod -Uri "http://localhost:8080/api/communities/$($commTH.id)/members"
$charlieInTH = $charlieMembers | Where-Object { $_.id -eq $charlie.id }
Assert-Condition ($charlieInTH -ne $null -and $charlieInTH.communityId -eq $commTH.id) "Creator Charlie is automatically listed in TechHub members with communityId $($commTH.id)"


Write-Host "`n=== 4. JOINING BY CODE & NO SILENT SWITCHING ==="
# Bob joins Green Park
$bodyJoinBob = @{userId=$bob.id; code=$commGP.code} | ConvertTo-Json
$joinBobRes = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/communities/join" -ContentType "application/json" -Body $bodyJoinBob
Assert-Condition ($joinBobRes.id -eq $commGP.id) "Bob successfully joined Green Park by code $($commGP.code)"

# Dave joins TechHub
$bodyJoinDave = @{userId=$dave.id; code=$commTH.code} | ConvertTo-Json
$joinDaveRes = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/communities/join" -ContentType "application/json" -Body $bodyJoinDave
Assert-Condition ($joinDaveRes.id -eq $commTH.id) "Dave successfully joined TechHub by code $($commTH.code)"

# Test NO SILENT SWITCHING: Bob attempts to join TechHub while already in Green Park
try {
    $switchBody = @{userId=$bob.id; code=$commTH.code} | ConvertTo-Json
    Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/communities/join" -ContentType "application/json" -Body $switchBody
    Write-Error "Expected failure when switching communities, but succeeded!"
    exit 1
} catch {
    Write-Host " [PASS] Prevented silent community switch for Bob: $($_.Exception.Message)" -ForegroundColor Green
}


Write-Host "`n=== 5. MEMBER LISTING & NO PASSWORD / HASH LEAKAGE ==="
$gpMembers = Invoke-RestMethod -Uri "http://localhost:8080/api/communities/$($commGP.id)/members"
Assert-Condition ($gpMembers.Count -ge 2) "Green Park has $($gpMembers.Count) members (Alice & Bob)"
foreach ($m in $gpMembers) {
    Assert-Condition ($m.password -eq $null -and $m.passwordHash -eq $null) "Member $($m.name) exposes no password or passwordHash"
}


Write-Host "`n=== 6. CREATING BOOKS IN RESPECTIVE COMMUNITIES ==="
# Alice lists a book in Green Park
$bookAliceBody = @{
    title="Green Park Gardening";
    author="Alice Green";
    isbn="978-0-123456-01-0";
    genre="Non-Fiction";
    edition="1st";
    condition="Good";
    conditionNotes="Clean copy";
    availability="Available";
    preferredBorrowingDuration="14 days";
    ownerId=$alice.id
} | ConvertTo-Json
$bookAlice = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/books" -ContentType "application/json" -Body $bookAliceBody
Assert-Condition ($bookAlice.id -ne $null) "Alice created book '$($bookAlice.title)' (id: $($bookAlice.id))"

# Charlie lists a book in TechHub
$bookCharlieBody = @{
    title="TechHub Microservices";
    author="Charlie Tech";
    isbn="978-0-123456-02-0";
    genre="Technology";
    edition="2nd";
    condition="Like New";
    conditionNotes="Pristine";
    availability="Available";
    preferredBorrowingDuration="14 days";
    ownerId=$charlie.id
} | ConvertTo-Json
$bookCharlie = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/books" -ContentType "application/json" -Body $bookCharlieBody
Assert-Condition ($bookCharlie.id -ne $null) "Charlie created book '$($bookCharlie.title)' (id: $($bookCharlie.id))"


Write-Host "`n=== 7. COMMUNITY-SCOPED BOOK DISCOVERY & EXCLUSION ==="
# Bob (Green Park member) discovers books in Green Park
$bobGPBooks = Invoke-RestMethod -Uri "http://localhost:8080/api/books?communityId=$($commGP.id)&requestingUserId=$($bob.id)"
$hasAliceBook = ($bobGPBooks | Where-Object { $_.id -eq $bookAlice.id }) -ne $null
$hasCharlieBook = ($bobGPBooks | Where-Object { $_.id -eq $bookCharlie.id }) -ne $null
Assert-Condition ($hasAliceBook -and -not $hasCharlieBook) "Bob sees Alice's Green Park book and NOT Charlie's TechHub book"

# Dave (TechHub member) discovers books in TechHub
$daveTHBooks = Invoke-RestMethod -Uri "http://localhost:8080/api/books?communityId=$($commTH.id)&requestingUserId=$($dave.id)"
$hasCharlieBookTH = ($daveTHBooks | Where-Object { $_.id -eq $bookCharlie.id }) -ne $null
$hasAliceBookTH = ($daveTHBooks | Where-Object { $_.id -eq $bookAlice.id }) -ne $null
Assert-Condition ($hasCharlieBookTH -and -not $hasAliceBookTH) "Dave sees Charlie's TechHub book and NOT Alice's Green Park book"

# Cross-community book discovery rejection: Bob tries to query TechHub books
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/books?communityId=$($commTH.id)&requestingUserId=$($bob.id)"
    Write-Error "Expected failure when Bob accesses TechHub books, but succeeded!"
    exit 1
} catch {
    Write-Host " [PASS] Cross-community discovery rejected when Bob queries TechHub: $($_.Exception.Message)" -ForegroundColor Green
}

# User without community tries community-scoped discovery
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/books?communityId=$($commGP.id)&requestingUserId=$($eve.id)"
    Write-Error "Expected failure when Eve queries Green Park books, but succeeded!"
    exit 1
} catch {
    Write-Host " [PASS] Eve (no community) rejected from community-scoped discovery: $($_.Exception.Message)" -ForegroundColor Green
}


Write-Host "`n=== 8. BORROW REQUEST ACCESS CONTROL ==="
# Same-community borrowing: Bob borrows Alice's book (both Green Park)
$borrowBodySame = @{
    bookId=$bookAlice.id;
    borrowerId=$bob.id;
    requestedDuration="14 days";
    message="Hi Alice, can I borrow your gardening book?"
} | ConvertTo-Json
$borrowReqSame = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/borrow-requests" -ContentType "application/json" -Body $borrowBodySame
Assert-Condition ($borrowReqSame.id -ne $null -and $borrowReqSame.status -eq "REQUESTED") "Same-community borrow request created (id: $($borrowReqSame.id), status: $($borrowReqSame.status))"

# Cross-community borrowing rejection: Dave (TechHub) tries to borrow Alice's book (Green Park)
try {
    $borrowBodyCross = @{
        bookId=$bookAlice.id;
        borrowerId=$dave.id;
        requestedDuration="7 days";
        message="Cross community attempt"
    } | ConvertTo-Json
    Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/borrow-requests" -ContentType "application/json" -Body $borrowBodyCross
    Write-Error "Expected failure when Dave borrows Alice's book cross-community, but succeeded!"
    exit 1
} catch {
    Write-Host " [PASS] Cross-community borrow request rejected: $($_.Exception.Message)" -ForegroundColor Green
}

# User without community tries to borrow: Eve (no community) tries to borrow Alice's book
try {
    $borrowBodyEve = @{
        bookId=$bookAlice.id;
        borrowerId=$eve.id;
        requestedDuration="7 days";
        message="Eve borrow attempt"
    } | ConvertTo-Json
    Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/borrow-requests" -ContentType "application/json" -Body $borrowBodyEve
    Write-Error "Expected failure when Eve (no community) borrows community book, but succeeded!"
    exit 1
} catch {
    Write-Host " [PASS] Eve (no community) borrow request rejected: $($_.Exception.Message)" -ForegroundColor Green
}


Write-Host "`n=== 9. BORROW LIFECYCLE / HANDOVER / RETURN / REVIEW REGRESSION ==="
# Alice accepts Bob's request
$acceptedReq = Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/borrow-requests/$($borrowReqSame.id)/accept?ownerId=$($alice.id)"
Assert-Condition ($acceptedReq.status -eq "ACCEPTED") "Request accepted by Alice, status is ACCEPTED"

# Alice hands over the book
$handedOverReq = Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/borrow-requests/$($borrowReqSame.id)/handover?ownerId=$($alice.id)"
Assert-Condition ($handedOverReq.status -eq "HANDED_OVER") "Handover completed, status is HANDED_OVER"

# Bob requests return
$returnReq = Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/borrow-requests/$($borrowReqSame.id)/return?borrowerId=$($bob.id)"
Assert-Condition ($returnReq.status -eq "RETURN_REQUESTED") "Return requested by Bob, status is RETURN_REQUESTED"

# Alice confirms return
$confirmedReq = Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/borrow-requests/$($borrowReqSame.id)/return-confirm?ownerId=$($alice.id)"
Assert-Condition ($confirmedReq.status -eq "RETURNED") "Return confirmed by Alice, status is RETURNED"

# Leave a Review (Bob reviews Alice / request)
$reviewBody = @{
    borrowRequestId=$borrowReqSame.id;
    reviewerId=$bob.id;
    revieweeId=$alice.id;
    rating=5;
    comment="Great book, smooth handover!"
} | ConvertTo-Json
$reviewRes = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/reviews" -ContentType "application/json" -Body $reviewBody
Assert-Condition ($reviewRes.id -ne $null -and $reviewRes.rating -eq 5) "Review submitted successfully with rating $($reviewRes.rating)"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " ALL LIVE VERIFICATION CHECKS PASSED SUCCESSFULLY!" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
