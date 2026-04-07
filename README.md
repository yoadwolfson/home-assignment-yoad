# In this README i will walk you through few things that helped me implement the ussignment.

# Project workflow

 i used a workflow built the following way 

  # 1 first step 

   planning using a planning  agent (the agent file is in the github repo under the name planner). 

   # input prompt used 

   "i am a junior developer and got a home assignment from a high tec company i am hoping to work in. i want you to help me plan how to execute this assignment and make it suitable for an interview. you may ask follow up questions if needed. this is the assignment ""you are given a list(products and prices) with duplicates and uneaven names example the product samsung galaxy s23 also shows up in hebrew as "סמסונ גלקסי s23" you need to write or define a script to merge the duplicates and make sure the price is the lowest from all the duplicates of each product"

   # output

    the Agent built a ready to implement plan (for other Agent/programmer) featured in plan repository As implmentPlan.

    in  the same context other Agent is called (A semi-implement agent) who gives a more detailed plan mentiond in plans also as furtherPlan.

# plan

 for planning further after a reasarch(using AI and planing agents) i came up with this plan   

 # assumptions

  # Data format

  the data would be saved in the format of a List of Products (a java object of string and int value).

  # What we call a duplicate 

  a duplicate may be:

   the same product in heberw/english

   the same product with different tabs/whith chars format.

   the same product with different upper/lower chars format.

   the same product with a typo.

  # Output format

  the output of the script is a list of pairs contains no duplicates and the lowest price for each products among the duplicates.

 # how the data is proccesed.

 1. Clean the product name.

   - convert to lowercase

   - remove punctuation and extra spaces

   - trim whitespace
 
 2. Normalize tokens.

   - map known Hebrew words to English canonical tokens

   - apply fuzzy matching only to small typos (keep the accepted distance small)

   - keep fuzzy matching script-aware so Hebrew compares with Hebrew and English compares with English
 
 3. Build a canonical key.

   - use brand and model as the main identity

   - treat family words like galaxy or air as optional when brand and model already identify the product
 
 4. Group and merge.

   - group products by the canonical key

   - keep the lowest price in each group

## Why this works

- `Samsung glaxy 23`, `samsung 23`, and `סמסונג גלאקסי 23` can match because the workflow removes formatting differences, fixes small typos, maps Hebrew to English, and keeps only the meaningful identity parts.

- `Samsung 23` and `Samsung 24` stay separate because the model number is part of the key.

- Full-name fuzzy matching is avoided because it can merge the wrong products.

## Main rules

- Exact mapping happens before fuzzy matching.

- Fuzzy matching is token-level, not whole-name-level.

- Digits are protected from fuzzy changes.

- Final grouping is exact after normalization.

## Result

The output is a deduplicated product list where each group keeps the cheapest item and the final names are normalized in a consistent way.
