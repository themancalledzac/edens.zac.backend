# edens.zac.backend

## Milestone Order

- ### **_Milestone 1:_**
    - Database Built
        - Tables Built
            - IMAGE
                - columns
                    - focalLength
                    - fStop
                    - shutterSpeed
                    - iso
                    - author
                    - lens
                    - lensSpecific
                    - camera
                    - date
                    - imageHeight
                    - imageWidth
                    - horizontal
                    - blackAndWhite
                    - rawFileName
                    - rating
                    - title
                    - projects[List] - Categories are 'events', a set list of images as a part of a whole.
                    - tags - Any associative tag ( bw, portugal, person, flower)
            - PROJECT
              - columns
                - title
                - location
                - date
                - images[List]
                - tags
            - TAGS
              - images[List]
              - projects[List]
        - Logic for passing Image to Program(Platform?)
            - Program(Platform?): takes client requests, verifies request(AUTH0?) adds data to various database tables
            - Upload Image.
    - Get EXIF Data from JPEG
        - https://stackoverflow.com/questions/16115851/read-image-metadata-from-single-file-with-java
        - Save that Data to Program
            - We would like all relevant image metadata to be added to the Image table.
    - Database built for AWS
- ### **_Milestone 2:_**
      - `CreateImage` call is built First. ( Should add to Image, AllImages, Adventure, AllAdventures )
      - `GetAllImage` call built Second ( just returning a full list of image objects with uuid, imageLocation, rating)
      - `GetImage` call built Third ( pass UUID, get ALL THE REST of the Data for that image in a big object )
- ### **_Milestone 3:_**
    - Create way of a 1 time scrape of all uploaded images in S3 bucket to add them to the database.
        - This needs to do a few things for an MVP: CreateImage call with location
- ### **_Milestone 4:_**
    - Create way of MINIFYING images, including recursively through all images already on S3
    - Update file structure on AWS. will need to run an application that does the following:
        - For each folder/file(?granular?)
            - Move RAW to Cold Storage ( Glacier )
            - Move JPEG_
- ### **_Milestone 5:_**
- Create a Folder Structure that is similar to our AWS S3 Bucket structure
    - Do we have a database table with a structure built in?
        - [{'photography':{'2022':{}, '2023':{}}}]
        - OR,
        - simple object: structure:
            - [{'name':'photography', 'importance': 1}, {'name': '2023', 'importance': 2}, {'name': '2022', 'importance': 2}, {'name': '2022_hidden_lake', 'importance': 3}]
    -
    - Mobile would be HUGE for this. be able to open a finder-like (dropdown, side panel or full screen) container that
      has (withAuth)

Backend Structure

- DB:
    - SQL
    - `Image` table
        - includes ALL Image data:
            - Name, Uuid, Category(nullable), location, film(boolean), tag(Array["String"]), Rating
    - `AllImages` table
        - List of All Images, including Uuid, Small Image Url, Rating
    - `AllAdventures` table
        - List of All Adventures, including Name, UUID, Date(range)
    - `Adventure` table
        - Includes Name, Array of Objects with Image Uuid, Rating
        - We COULD not have this table at all, and our `GetAdventureByUuid` could simply be a GetAllImages WHERE
          image.adventureUuid = $x;
- CRUD CALLS
    - `GetImage` - Get Image by Uuid
    - `GetAllImages` - Get All Images
    - `GetImagesByRating` - Get All Images by rating
        - This could return the following:
            - 3 star - Lowest that we export/view ( Unless Logged In )
                - When we do an adventure, it will be filled more with these as there should be a higher 3 to 4/5 ratio
            - 4 star - Nearly Top Tier - bigger shots on stories/adventures
            - 5 star. THE BEST. These are Covers, Full Screen. Could be under PRINTS section?
    - `GetImagesByAdventureUuid` = Get All Images with matching String param of "Adventure_Uuid"
    - `GetAllAdventures` - Returns an Array of objects with adventure Name and Uuid
    - `GetAdventureByUuid` - Returns an Object of all Adventure data by Uuid, including name, date, description,
- `CreateImage` - Upload Image - Upload Image to AWS - Save URL to variable - Create new Uuid - Star Rating (required
  param); - Image data we want:
    - Date, Location(?), Aperture, Shutter Speed, Rating(? dunno if this works)
    - Add to `Image` table with ALL data, including Uuid, rating, adventure name. - Add to `AllImages` table with Uuid,
      Rating, Category_Name
        - if (Adventure_Name Param) ( nullable field on `CreateImage` call) Add to `Adventure` Table
        - `ChangeImageRating`
            - Takes Image Uuid, newRating, Updates Image table, AllImages table
        - `GetImageByTag`
            - Could be a unique way of having specific tags outside of location/adventure
            - Backburner, eventual if we NEED it.
            -

Notes

- Star Rating
    - What we could do with this have a more specified order, showing, or hiding altogether, based on star rating.
    - If 10 images are in a grouping, but only 5 can be shown at a time, we would choose the highest rated images first,
      and then progress in whatever other order we'd want.
    - Guest, or not logged in users would be presented the base page, where only top rated images are visible.
    - As such, We could also have ALL images available only when LOGGED IN ( AUTH0 ), otherwise the only images that
      load are the top-rated.
        - Could also have a `LoggedInOnly` [STATE] that would render a toggle to `SHOW_ALL`, or something like that.

Logic Image carousel Layout Images 4 + 5 stars are full sized (for horizontal), and side by side for vertical. Images 3
stars are as follows:

createImageCarousel(Array images) => {

    // Images will probably be more of an Array of Image Objects?

    While (images.length) {

        new array[] newSmallVerticalRow, newSmallHorizontalRow, newLargeVerticalRow, newLargeHorizontalRow; 
        new array[] rowArray = [newSmallVerticalRow, newSmallHorizontalRow, newLargeVerticalRow, newLargeHorizontalRow];
        new Boolean preferLarge = true;

        addRow(images) => {
            checkComplete();
            if (!completed) => {
                for (image in images) {
                    addImage(image);
                    if (checkComplete() => return)
                }
            }

addImage(images) => {

                if(newImage.star == 3) => 
                    if(newImage.dir == vertical) => SmallVerticalRow(newImage);
                    if(newImage.dir == horizontal) => SmallHorizontalRow(newImage);
                if(newImage.star >= 4) =>
                    if(newImage.dir == vertical) => LargeVerticalRow(newImage);
                    if(newImage.dir == horizontal) => LargeHorizontalRow(newImage);
                verifyRowCreated;
                setNewFalse;
            )
            if(!new) => {
                for (row in rowArray) {
                    if (row.length == row.maxLength) =>
                        return row
                }
            }
        } 

        addImage(images) => {
              if(newImage.star == 3) => 
                    if(newImage.dir == vertical) => SmallVerticalRow(newImage);
                    if(newImage.dir == horizontal) => SmallHorizontalRow(newImage);
                if(newImage.star >= 4) =>
                    if(newImage.dir == vertical) => LargeVerticalRow(newImage);
                    if(newImage.dir == horizontal) => LargeHorizontalRow(newImage);
                verifyRowCreated;
                setNewFalse;
        }
    }

}

class ImageRow =>
int length; int maxLength; Array [Images]

class SmallVerticalRow extends ImageRow =>
int length; int maxLength = 4;

// TODO: We need a new different, Mix row. if Small(3star)
// TODO: contains 1 horizontal and 2 vertical // TODO: Do we, when creating, take 1 image out of IMAGE, and into Store(
short term storage image bucket)
// TODO: if we do this, then after adding image, we check if any type of row is able to be built, and THEN build it. //
TODO: we could have an alternator value (preferLarge==true/false) that prefers a Large row, UNTIL we have gotten one //
TODO: aka, if(preferLarge) => check large row options first(aka, do we have 1 horizontal, or two vertical)
// TODO: aka, if(!preferLarge) => check small row opetion first


- ### **_TODO_**
  - Photography
    - FOR ALL IMAGE Folders, 
      - delete all Non-keepers
      - delete any that don't serve a purpose
      - 
      - save RAW files to AWS Glacier storage
        - 1000GB for $3.60/month
  - Backend
    - Database Creation
      - Image Table Object finalized
      - Database connection working
      - Save Image to Database DB Call created
      - Get Image from Database DB Call created
      - Get Images by ____ DB Calls figured out.
        - What are our main calls we want here?
    - AWS Connection
      - Test 'Add' to S3 bucket
      - Test return location after upload to S3 bucket
      - Test get image from S3 bucket by imageLocation
      - Test get S3 bucket structure as tree (?)